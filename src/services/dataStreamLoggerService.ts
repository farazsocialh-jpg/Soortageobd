/**
 * Automatic Data Stream Logger Service for Kia Sportage OBD-II
 * 
 * Automatically initiates high-speed telemetry logging as soon as the car
 * ignition turns on and Bluetooth connects.
 * 
 * Records every polled ECU sensor, timestamp, speed, RPM, load, coolant,
 * fuel trims, battery voltage, and raw ELM327 packets into persistent storage.
 * 
 * Provides:
 * - Auto-start on ignition connection
 * - Real-time stream subscription hooks
 * - Live session metrics (Hz rate, max speed, peak RPM, voltage swings)
 * - WakeLock prevention of screen timeout while mounted on dashboard
 * - Export to CSV, JSON, and Raw OBD Log
 * - Persistent trip log history
 */

import { LiveTelemetry, ReadingState } from '../types/obd';
import { audioChimeService } from './audioChimeService';

export interface TelemetryLogRecord {
  id: string;
  isoTime: string;
  epochMs: number;
  elapsedSec: number;
  speedKmh: number | null;
  speedMph: number | null;
  rpm: number | null;
  coolantTempC: number | null;
  intakeTempC: number | null;
  engineLoadPct: number | null;
  throttlePosPct: number | null;
  batteryVolts: number | null;
  gear: number;
  stftPct: number | null;
  ltftPct: number | null;
  mapKpa: number | null;
  mafGs: number | null;
  fuelLevelPct: number | null;
  rawPacket: string;
}

export interface LogSessionSummary {
  id: string;
  startTime: string;
  endTime: string | null;
  durationSec: number;
  startEpochMs: number;
  vehicleName: string;
  adapterName: string;
  totalRecords: number;
  sampleRateHz: number;
  maxSpeedKmh: number;
  maxRpm: number;
  maxCoolantC: number;
  minBatteryV: number;
  maxBatteryV: number;
  status: 'RECORDING' | 'COMPLETED' | 'PAUSED';
  autoInitiated: boolean;
}

export interface LogSession extends LogSessionSummary {
  records: TelemetryLogRecord[];
}

export type LogRecordListener = (record: TelemetryLogRecord) => void;
export type SessionUpdateListener = (session: LogSession | null) => void;
export type LoggingStateListener = (isLogging: boolean) => void;

class DataStreamLoggerService {
  private currentSession: LogSession | null = null;
  private isLogging: boolean = false;
  private autoLogEnabled: boolean = true;
  private wakeLock: any = null;

  private recordListeners: Set<LogRecordListener> = new Set();
  private sessionListeners: Set<SessionUpdateListener> = new Set();
  private stateListeners: Set<LoggingStateListener> = new Set();

  // Rate calculation
  private samplesInCurrentSecond: number = 0;
  private currentHz: number = 0;
  private hzTimer: any = null;

  // Max records kept in memory per session
  private readonly MAX_IN_MEMORY_RECORDS = 40000;

  constructor() {
    const savedAutoLog = localStorage.getItem('sportage_obd_autolog_enabled');
    if (savedAutoLog !== null) {
      this.autoLogEnabled = savedAutoLog === 'true';
    }

    // Start rate calculator
    this.hzTimer = setInterval(() => {
      this.currentHz = this.samplesInCurrentSecond;
      this.samplesInCurrentSecond = 0;
      if (this.currentSession && this.isLogging) {
        this.currentSession.sampleRateHz = this.currentHz;
        this.currentSession.durationSec = Math.round(
          (Date.now() - this.currentSession.startEpochMs) / 1000
        );
        this.notifySession();
      }
    }, 1000);
  }

  public isAutoLogEnabled(): boolean {
    return this.autoLogEnabled;
  }

  public setAutoLogEnabled(enabled: boolean) {
    this.autoLogEnabled = enabled;
    localStorage.setItem('sportage_obd_autolog_enabled', String(enabled));
  }

  public getIsLogging(): boolean {
    return this.isLogging;
  }

  public getCurrentSession(): LogSession | null {
    return this.currentSession;
  }

  public getCurrentHz(): number {
    return this.currentHz;
  }

  /**
   * Automatically triggered when Bluetooth connection completes.
   */
  public startLoggingSession(
    source: 'AUTO_IGNITION' | 'MANUAL' = 'AUTO_IGNITION',
    adapterName: string = 'ELM327 BLE Adapter'
  ): LogSession {
    if (this.isLogging && this.currentSession) {
      return this.currentSession;
    }

    const now = new Date();
    const sessionId = `TRIP-${now.getFullYear()}${String(now.getMonth() + 1).padStart(2, '0')}${String(now.getDate()).padStart(2, '0')}-${String(now.getHours()).padStart(2, '0')}${String(now.getMinutes()).padStart(2, '0')}${String(now.getSeconds()).padStart(2, '0')}`;

    this.currentSession = {
      id: sessionId,
      startTime: now.toISOString(),
      endTime: null,
      durationSec: 0,
      startEpochMs: Date.now(),
      vehicleName: 'Kia Sportage 2.0 MPI (Nu G4NA)',
      adapterName,
      totalRecords: 0,
      sampleRateHz: 0,
      maxSpeedKmh: 0,
      maxRpm: 0,
      maxCoolantC: 0,
      minBatteryV: 99,
      maxBatteryV: 0,
      status: 'RECORDING',
      autoInitiated: source === 'AUTO_IGNITION',
      records: []
    };

    this.isLogging = true;
    this.samplesInCurrentSecond = 0;

    // Request Screen Wake Lock so screen does not turn off in car dashboard mount
    this.requestWakeLock();

    // Sound alert chime
    audioChimeService.playLoggingStarted();

    this.notifyState();
    this.notifySession();

    return this.currentSession;
  }

  /**
   * Log a new telemetry tick into the active session
   */
  public logTelemetrySample(telemetry: LiveTelemetry, rawPacket: string = '') {
    if (!this.isLogging || !this.currentSession) return;

    const extractVal = (reading: ReadingState<number>): number | null => {
      return reading.status === 'LIVE' ? reading.value : null;
    };

    const speed = extractVal(telemetry.speed);
    const rpm = extractVal(telemetry.rpm);
    const coolant = extractVal(telemetry.coolantTemp);
    const intake = extractVal(telemetry.intakeTemp);
    const load = extractVal(telemetry.engineLoad);
    const throttle = extractVal(telemetry.throttlePosition);
    const battery = extractVal(telemetry.batteryVoltage);
    const stft = extractVal(telemetry.stftBank1);
    const ltft = extractVal(telemetry.ltftBank1);
    const map = extractVal(telemetry.map);
    const maf = extractVal(telemetry.maf);
    const fuel = extractVal(telemetry.fuelLevel);

    const nowMs = Date.now();
    const elapsedSec = Number(((nowMs - this.currentSession.startEpochMs) / 1000).toFixed(2));

    const record: TelemetryLogRecord = {
      id: `${nowMs}-${Math.random().toString(36).substring(2, 6)}`,
      isoTime: new Date(nowMs).toISOString(),
      epochMs: nowMs,
      elapsedSec,
      speedKmh: speed,
      speedMph: speed !== null ? Math.round(speed * 0.621371) : null,
      rpm,
      coolantTempC: coolant,
      intakeTempC: intake,
      engineLoadPct: load,
      throttlePosPct: throttle,
      batteryVolts: battery,
      gear: telemetry.gear || 0,
      stftPct: stft,
      ltftPct: ltft,
      mapKpa: map,
      mafGs: maf,
      fuelLevelPct: fuel,
      rawPacket
    };

    // Update session aggregates
    this.currentSession.records.push(record);
    if (this.currentSession.records.length > this.MAX_IN_MEMORY_RECORDS) {
      this.currentSession.records.shift();
    }

    this.currentSession.totalRecords += 1;
    this.samplesInCurrentSecond += 1;

    if (speed !== null && speed > this.currentSession.maxSpeedKmh) {
      this.currentSession.maxSpeedKmh = speed;
    }
    if (rpm !== null && rpm > this.currentSession.maxRpm) {
      this.currentSession.maxRpm = rpm;
    }
    if (coolant !== null && coolant > this.currentSession.maxCoolantC) {
      this.currentSession.maxCoolantC = coolant;
    }
    if (battery !== null) {
      if (battery < this.currentSession.minBatteryV) this.currentSession.minBatteryV = battery;
      if (battery > this.currentSession.maxBatteryV) this.currentSession.maxBatteryV = battery;
    }

    // Dispatch to live record listeners
    this.recordListeners.forEach(cb => cb(record));
  }

  /**
   * Stop the active session and save to past sessions history
   */
  public stopLoggingSession(reason: string = 'NORMAL'): LogSession | null {
    if (!this.currentSession) return null;

    this.isLogging = false;
    this.currentSession.status = 'COMPLETED';
    this.currentSession.endTime = new Date().toISOString();
    this.currentSession.durationSec = Math.round(
      (Date.now() - this.currentSession.startEpochMs) / 1000
    );

    // Save session summary to localStorage list
    this.saveSessionToHistory(this.currentSession);

    // Release Wake Lock
    this.releaseWakeLock();

    const finalized = this.currentSession;
    this.notifyState();
    this.notifySession();

    return finalized;
  }

  /**
   * Save session metadata and sampled preview to localStorage history
   */
  private saveSessionToHistory(session: LogSession) {
    try {
      const stored = localStorage.getItem('sportage_obd_log_history');
      let history: LogSessionSummary[] = stored ? JSON.parse(stored) : [];

      const summary: LogSessionSummary = {
        id: session.id,
        startTime: session.startTime,
        endTime: session.endTime,
        durationSec: session.durationSec,
        startEpochMs: session.startEpochMs,
        vehicleName: session.vehicleName,
        adapterName: session.adapterName,
        totalRecords: session.totalRecords,
        sampleRateHz: session.sampleRateHz,
        maxSpeedKmh: session.maxSpeedKmh,
        maxRpm: session.maxRpm,
        maxCoolantC: session.maxCoolantC,
        minBatteryV: session.minBatteryV === 99 ? 0 : session.minBatteryV,
        maxBatteryV: session.maxBatteryV,
        status: 'COMPLETED',
        autoInitiated: session.autoInitiated
      };

      // Keep up to 20 past sessions in history summary
      history = [summary, ...history.filter(h => h.id !== session.id)].slice(0, 20);
      localStorage.setItem('sportage_obd_log_history', JSON.stringify(history));

      // Also persist the full session data (or sample) with a key
      // Store recent records
      const sessionData = {
        summary,
        sampleRecords: session.records.slice(-1000)
      };
      localStorage.setItem(`sportage_log_data_${session.id}`, JSON.stringify(sessionData));
    } catch {
      // Storage quota or parsing error
    }
  }

  public getSavedSessionsHistory(): LogSessionSummary[] {
    try {
      const stored = localStorage.getItem('sportage_obd_log_history');
      return stored ? JSON.parse(stored) : [];
    } catch {
      return [];
    }
  }

  public clearSavedSessionsHistory() {
    try {
      const history = this.getSavedSessionsHistory();
      history.forEach(s => localStorage.removeItem(`sportage_log_data_${s.id}`));
      localStorage.removeItem('sportage_obd_log_history');
    } catch {
      // Ignore
    }
  }

  // --- Export utilities ---

  /**
   * Export records to Automotive CSV (compatible with Excel, MegaLogViewer, RaceRender)
   */
  public exportToCsv(sessionToExport?: LogSession): string {
    const session = sessionToExport || this.currentSession;
    if (!session || session.records.length === 0) {
      return 'No telemetry data to export.';
    }

    const headers = [
      'Timestamp_ISO',
      'Elapsed_Sec',
      'Speed_kmh',
      'Speed_mph',
      'Engine_RPM',
      'Gear',
      'Throttle_pct',
      'Engine_Load_pct',
      'Coolant_C',
      'Intake_C',
      'Battery_V',
      'STFT_Bank1_pct',
      'LTFT_Bank1_pct',
      'MAP_kPa',
      'MAF_gs',
      'Fuel_Level_pct',
      'Raw_Packet'
    ];

    const lines = [headers.join(',')];

    for (const r of session.records) {
      const row = [
        r.isoTime,
        r.elapsedSec,
        r.speedKmh ?? '',
        r.speedMph ?? '',
        r.rpm ?? '',
        r.gear ?? '',
        r.throttlePosPct ?? '',
        r.engineLoadPct ?? '',
        r.coolantTempC ?? '',
        r.intakeTempC ?? '',
        r.batteryVolts ?? '',
        r.stftPct ?? '',
        r.ltftPct ?? '',
        r.mapKpa ?? '',
        r.mafGs ?? '',
        r.fuelLevelPct ?? '',
        `"${(r.rawPacket || '').replace(/"/g, '""')}"`
      ];
      lines.push(row.join(','));
    }

    return lines.join('\n');
  }

  /**
   * Trigger automatic browser file download
   */
  public downloadCsv(filename?: string) {
    const session = this.currentSession;
    if (!session) return;
    const csv = this.exportToCsv(session);
    const blob = new Blob([csv], { type: 'text/csv;charset=utf-8;' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.setAttribute('download', filename || `${session.id}.csv`);
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    URL.revokeObjectURL(url);
  }

  public downloadJson(filename?: string) {
    const session = this.currentSession;
    if (!session) return;
    const jsonStr = JSON.stringify(session, null, 2);
    const blob = new Blob([jsonStr], { type: 'application/json' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.setAttribute('download', filename || `${session.id}.json`);
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    URL.revokeObjectURL(url);
  }

  // --- Screen WakeLock ---
  private async requestWakeLock() {
    if (typeof navigator !== 'undefined' && 'wakeLock' in navigator) {
      try {
        this.wakeLock = await (navigator as any).wakeLock.request('screen');
      } catch {
        // WakeLock rejected or not allowed
      }
    }
  }

  private async releaseWakeLock() {
    if (this.wakeLock) {
      try {
        await this.wakeLock.release();
        this.wakeLock = null;
      } catch {
        // Ignore
      }
    }
  }

  // --- Event Subscriptions ---
  public onRecord(cb: LogRecordListener): () => void {
    this.recordListeners.add(cb);
    return () => this.recordListeners.delete(cb);
  }

  public onSessionUpdate(cb: SessionUpdateListener): () => void {
    this.sessionListeners.add(cb);
    return () => this.sessionListeners.delete(cb);
  }

  public onStateChange(cb: LoggingStateListener): () => void {
    this.stateListeners.add(cb);
    return () => this.stateListeners.delete(cb);
  }

  private notifySession() {
    this.sessionListeners.forEach(cb => cb(this.currentSession));
  }

  private notifyState() {
    this.stateListeners.forEach(cb => cb(this.isLogging));
  }
}

export const dataStreamLoggerService = new DataStreamLoggerService();

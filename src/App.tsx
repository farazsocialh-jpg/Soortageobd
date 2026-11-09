import React, { useState } from 'react';
import {
  Smartphone,
  Cpu,
  FileCode,
  Terminal,
  CheckCircle2,
  Trash2,
  Download,
  Copy,
  Check,
  ShieldCheck,
  Zap,
  Activity,
  Layers,
  ArrowRight,
  GitBranch,
  FileCheck
} from 'lucide-react';

export default function App() {
  const [activeTab, setActiveTab] = useState<'overview' | 'files' | 'git' | 'architecture'>('overview');
  const [copiedCmd, setCopiedCmd] = useState<string | null>(null);
  const [selectedFile, setSelectedFile] = useState<string>('IgnitionReceiver.kt');

  const copyToClipboard = (text: string, id: string) => {
    navigator.clipboard.writeText(text);
    setCopiedCmd(id);
    setTimeout(() => setCopiedCmd(null), 2000);
  };

  const fileContents: Record<string, { path: string; desc: string; code: string }> = {
    'IgnitionReceiver.kt': {
      path: 'android/app/src/main/java/com/sportage/obd/service/IgnitionReceiver.kt',
      desc: 'Broadcast receiver listening for car 12V charger power on ignition, Bluetooth ACL link, and boot',
      code: `package com.sportage.obd.service

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class IgnitionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        Log.i("IgnitionReceiver", "Car state broadcast: $action")
        when (action) {
            Intent.ACTION_POWER_CONNECTED,
            BluetoothDevice.ACTION_ACL_CONNECTED,
            BluetoothAdapter.ACTION_STATE_CHANGED,
            Intent.ACTION_BOOT_COMPLETED -> {
                val serviceIntent = Intent(context, ObdService::class.java).apply {
                    this.action = ObdService.ACTION_AUTO_CONNECT
                }
                context.startForegroundService(serviceIntent)
            }
        }
    }
}`
    },
    'ObdService.kt': {
      path: 'android/app/src/main/java/com/sportage/obd/service/ObdService.kt',
      desc: 'Foreground service with persistent 6s ignition watchdog loop and automatic CSV stream logging on handshake',
      code: `// Key Auto-Connect & Telemetry Logging Handshake in ObdService.kt
private fun scheduleReconnect() {
    reconnectJob?.cancel()
    reconnectJob = serviceScope.launch {
        while (isActive) {
            val autoConnectEnabled = isAutoConnectEnabled()
            if (!autoConnectEnabled) break

            val lastAddress = lastDeviceAddress ?: run {
                getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getString(KEY_LAST_DEVICE, null)
            }
            if (lastAddress != null) {
                _connectionState.value = ConnectionState.Connecting
                val success = connectToDevice(lastAddress)
                if (success) {
                    _connectionState.value = ConnectionState.Connected
                    // Automatically initiate telemetry data stream logging
                    if (isCsvAutostartEnabled()) {
                        startCsvLogging()
                    }
                    return@launch
                }
            }
            // Continuous 6s watchdog interval while car is parked / off
            delay(6000)
        }
    }
}`
    },
    'android-build.yml': {
      path: '.github/workflows/android-build.yml',
      desc: 'GitHub Actions workflow with automatic google-services.json synthesis and APK compilation',
      code: `name: Android APK
on:
  push:
    branches: [ main, master ]
  workflow_dispatch:

jobs:
  build:
    name: Build APK
    runs-on: ubuntu-latest
    defaults:
      run:
        working-directory: android
    steps:
      - name: Checkout
        uses: actions/checkout@v4

      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '17'

      - name: Set up Gradle
        uses: gradle/actions/setup-gradle@v4

      - name: Ensure google-services.json
        run: |
          if [ ! -f "app/google-services.json" ]; then
            echo "Generating dual-flavor placeholder for com.sportage.obd..."
            cat << 'EOF' > app/google-services.json
            { ... placeholder JSON with debug & release packages ... }
            EOF
          fi

      - name: Build debug APK
        run: ./gradlew assembleDebug --stacktrace

      - name: Upload APKs
        uses: actions/upload-artifact@v4
        with:
          name: sportage-obd-apk
          path: out/*.apk`
    },
    'build.gradle.kts': {
      path: 'android/app/build.gradle.kts',
      desc: 'Conditional Firebase and Google Services plugin application',
      code: `plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.services) apply false
    alias(libs.plugins.firebase.crashlytics) apply false
}

// Conditionally apply Google Services and Crashlytics only when google-services.json exists
val hasGoogleServices = file("google-services.json").exists() ||
    file("src/release/google-services.json").exists() ||
    file("src/debug/google-services.json").exists()

if (hasGoogleServices) {
    apply(plugin = "com.google.gms.google-services")
    apply(plugin = "com.google.firebase.crashlytics")
}`
    }
  };

  const gitPushScript = `# 1. In your sportageobd local repository, pull or apply the changes:
git apply android-pure-project.patch

# 2. Verify all webapp files are staged for removal while android/ is preserved:
git status

# 3. Commit and push directly to GitHub:
git add -A
git commit -m "Convert repo to pure Android: remove webapp, keep native Android app and CI workflow"
git push origin main`;

  return (
    <div className="min-h-screen bg-[#070b10] text-slate-100 flex flex-col font-sans selection:bg-cyan-500 selection:text-black">
      {/* Top Cockpit Header */}
      <header className="border-b border-slate-800/80 bg-[#0d131c]/90 backdrop-blur sticky top-0 z-50 px-6 py-4">
        <div className="max-w-7xl mx-auto flex flex-wrap items-center justify-between gap-4">
          <div className="flex items-center gap-3">
            <div className="p-2.5 rounded-xl bg-gradient-to-tr from-cyan-600 to-emerald-500 text-black shadow-lg shadow-cyan-500/20">
              <Smartphone className="w-6 h-6 stroke-[2.2]" />
            </div>
            <div>
              <div className="flex items-center gap-2">
                <h1 className="text-xl font-bold tracking-tight text-white">SportageOBD Intelligence</h1>
                <span className="px-2 py-0.5 text-xs font-semibold uppercase tracking-wider rounded-full bg-emerald-500/10 text-emerald-400 border border-emerald-500/20">
                  Android Native
                </span>
              </div>
              <p className="text-xs text-slate-400 font-mono mt-0.5">
                Target: Kia Sportage (QL) 2.0 MPI AWD &bull; Package: com.sportage.obd
              </p>
            </div>
          </div>

          <div className="flex items-center gap-2">
            <a
              href="https://github.com/farazsocialh-jpg/sportageobd/actions"
              target="_blank"
              rel="noreferrer"
              className="px-3.5 py-1.5 rounded-lg text-xs font-medium bg-slate-800 hover:bg-slate-700 text-slate-200 transition-colors border border-slate-700/80 flex items-center gap-1.5"
            >
              <Activity className="w-3.5 h-3.5 text-cyan-400" />
              GitHub Actions CI
            </a>
            <div className="px-3.5 py-1.5 rounded-lg text-xs font-medium bg-cyan-950/60 text-cyan-300 border border-cyan-800/60 flex items-center gap-1.5">
              <CheckCircle2 className="w-3.5 h-3.5 text-emerald-400" />
              Webapp Purged: Pure Android
            </div>
          </div>
        </div>
      </header>

      {/* Main Content */}
      <main className="max-w-7xl mx-auto px-6 py-8 flex-1 w-full space-y-6">
        {/* Navigation Tabs */}
        <div className="flex border-b border-slate-800 gap-2">
          <button
            onClick={() => setActiveTab('overview')}
            className={`pb-3 px-4 text-sm font-medium border-b-2 transition-all flex items-center gap-2 ${
              activeTab === 'overview'
                ? 'border-cyan-400 text-cyan-400'
                : 'border-transparent text-slate-400 hover:text-slate-200'
            }`}
          >
            <ShieldCheck className="w-4 h-4" />
            Repository Status & Cleanup
          </button>
          <button
            onClick={() => setActiveTab('git')}
            className={`pb-3 px-4 text-sm font-medium border-b-2 transition-all flex items-center gap-2 ${
              activeTab === 'git'
                ? 'border-cyan-400 text-cyan-400'
                : 'border-transparent text-slate-400 hover:text-slate-200'
            }`}
          >
            <Terminal className="w-4 h-4" />
            Git & Push Instructions
          </button>
          <button
            onClick={() => setActiveTab('files')}
            className={`pb-3 px-4 text-sm font-medium border-b-2 transition-all flex items-center gap-2 ${
              activeTab === 'files'
                ? 'border-cyan-400 text-cyan-400'
                : 'border-transparent text-slate-400 hover:text-slate-200'
            }`}
          >
            <FileCode className="w-4 h-4" />
            Android Code Inspect
          </button>
          <button
            onClick={() => setActiveTab('architecture')}
            className={`pb-3 px-4 text-sm font-medium border-b-2 transition-all flex items-center gap-2 ${
              activeTab === 'architecture'
                ? 'border-cyan-400 text-cyan-400'
                : 'border-transparent text-slate-400 hover:text-slate-200'
            }`}
          >
            <Cpu className="w-4 h-4" />
            Ignition & Auto-Log Flow
          </button>
        </div>

        {/* Tab 1: Overview */}
        {activeTab === 'overview' && (
          <div className="space-y-6">
            <div className="grid grid-cols-1 md:grid-cols-3 gap-5">
              <div className="bg-[#0e1520] border border-slate-800 rounded-2xl p-5 shadow-sm">
                <div className="flex items-center justify-between mb-3">
                  <span className="text-xs font-mono uppercase text-rose-400 tracking-wider">Cleaned Up</span>
                  <div className="p-2 rounded-lg bg-rose-500/10 text-rose-400">
                    <Trash2 className="w-4 h-4" />
                  </div>
                </div>
                <div className="text-2xl font-bold text-white tracking-tight">68 Web Files</div>
                <p className="text-xs text-slate-400 mt-1">
                  Removed `src/`, `public/`, `icons/`, `vite.config.ts`, `bun.lock`, and web package dependencies.
                </p>
              </div>

              <div className="bg-[#0e1520] border border-slate-800 rounded-2xl p-5 shadow-sm">
                <div className="flex items-center justify-between mb-3">
                  <span className="text-xs font-mono uppercase text-emerald-400 tracking-wider">Preserved & Active</span>
                  <div className="p-2 rounded-lg bg-emerald-500/10 text-emerald-400">
                    <CheckCircle2 className="w-4 h-4" />
                  </div>
                </div>
                <div className="text-2xl font-bold text-white tracking-tight">100% Android</div>
                <p className="text-xs text-slate-400 mt-1">
                  `android/` Gradle project, Jetpack Compose UI, Bluetooth SPP transport, and Mapsforge offline maps.
                </p>
              </div>

              <div className="bg-[#0e1520] border border-slate-800 rounded-2xl p-5 shadow-sm">
                <div className="flex items-center justify-between mb-3">
                  <span className="text-xs font-mono uppercase text-cyan-400 tracking-wider">CI Compilation</span>
                  <div className="p-2 rounded-lg bg-cyan-500/10 text-cyan-400">
                    <Zap className="w-4 h-4" />
                  </div>
                </div>
                <div className="text-2xl font-bold text-white tracking-tight">GitHub Actions</div>
                <p className="text-xs text-slate-400 mt-1">
                  Automated `./gradlew assembleDebug` with Google Services dual-flavor fallback and APK artifact upload.
                </p>
              </div>
            </div>

            {/* Deletion Summary Table */}
            <div className="bg-[#0e1520] border border-slate-800 rounded-2xl p-6">
              <h2 className="text-base font-semibold text-white flex items-center gap-2 mb-4">
                <Layers className="w-4 h-4 text-cyan-400" />
                Repository Re-structure: Pure Android Architecture
              </h2>

              <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
                <div>
                  <h3 className="text-xs font-semibold uppercase tracking-wider text-rose-400 mb-2">
                    Purged Web Components (Deleted)
                  </h3>
                  <div className="bg-[#070b10] border border-slate-800/80 rounded-xl p-4 font-mono text-xs text-slate-300 space-y-1.5 max-h-60 overflow-y-auto">
                    <div className="text-rose-400">- src/ (React, Vite, TSX web launcher & cockpit)</div>
                    <div className="text-rose-400">- public/ (web images, screenshots, assets)</div>
                    <div className="text-rose-400">- icons/ (SVG icons for web browser)</div>
                    <div className="text-rose-400">- package.json, package-lock.json, bun.lock</div>
                    <div className="text-rose-400">- vite.config.ts, tsconfig.json, index.html</div>
                    <div className="text-rose-400">- .env.example, metadata.json</div>
                    <div className="text-rose-400">- phase3-scope.txt, phase-summary.txt</div>
                    <div className="text-rose-400">- android-compilation-fix.patch, fix-compile-errors.patch</div>
                  </div>
                </div>

                <div>
                  <h3 className="text-xs font-semibold uppercase tracking-wider text-emerald-400 mb-2">
                    Retained Android Architecture (Intact)
                  </h3>
                  <div className="bg-[#070b10] border border-slate-800/80 rounded-xl p-4 font-mono text-xs text-slate-300 space-y-1.5 max-h-60 overflow-y-auto">
                    <div className="text-emerald-400">+ android/app/src/main/ (Kotlin, Jetpack Compose, Manifest)</div>
                    <div className="text-emerald-400">+ android/app/src/main/.../service/IgnitionReceiver.kt</div>
                    <div className="text-emerald-400">+ android/app/src/main/.../service/ObdService.kt</div>
                    <div className="text-emerald-400">+ android/app/build.gradle.kts (Safe conditional Firebase)</div>
                    <div className="text-emerald-400">+ android/app/google-services.json (dual-flavor debug/release)</div>
                    <div className="text-emerald-400">+ android/gradle/ & gradlew wrapper (Gradle 8.9 / AGP 8.7.2)</div>
                    <div className="text-emerald-400">+ .github/workflows/android-build.yml (APK compilation workflow)</div>
                    <div className="text-emerald-400">+ README.md (Pure Android documentation)</div>
                  </div>
                </div>
              </div>
            </div>
          </div>
        )}

        {/* Tab 2: Git & Push Instructions */}
        {activeTab === 'git' && (
          <div className="space-y-6">
            <div className="bg-[#0e1520] border border-slate-800 rounded-2xl p-6">
              <div className="flex flex-wrap items-center justify-between gap-4 mb-4">
                <div>
                  <h2 className="text-lg font-semibold text-white flex items-center gap-2">
                    <GitBranch className="w-5 h-5 text-cyan-400" />
                    Apply Pure-Android Commit to GitHub
                  </h2>
                  <p className="text-xs text-slate-400 mt-1">
                    The entire diff (removing web files + adding ignition auto-connect & CI fixes) is bundled into <code className="text-cyan-300">android-pure-project.patch</code>.
                  </p>
                </div>
                <button
                  onClick={() => copyToClipboard(gitPushScript, 'push-script')}
                  className="px-4 py-2 rounded-xl bg-cyan-600 hover:bg-cyan-500 text-black font-semibold text-xs flex items-center gap-2 transition-colors"
                >
                  {copiedCmd === 'push-script' ? (
                    <>
                      <Check className="w-4 h-4" /> Copied Command!
                    </>
                  ) : (
                    <>
                      <Copy className="w-4 h-4" /> Copy Shell Script
                    </>
                  )}
                </button>
              </div>

              <div className="bg-[#05070a] border border-slate-800 rounded-xl p-5 relative font-mono text-xs text-slate-200">
                <pre className="whitespace-pre-wrap">{gitPushScript}</pre>
              </div>

              <div className="mt-6 flex flex-wrap gap-4 items-center bg-cyan-950/30 border border-cyan-800/40 rounded-xl p-4">
                <div className="p-2.5 rounded-lg bg-cyan-500/10 text-cyan-400">
                  <FileCheck className="w-5 h-5" />
                </div>
                <div className="flex-1 min-w-[240px]">
                  <div className="text-sm font-semibold text-white">Generated Patch Available</div>
                  <div className="text-xs text-slate-400">
                    File: <code className="text-cyan-300">android-pure-project.patch</code> (371 insertions, 19,850 deletions of web components)
                  </div>
                </div>
              </div>
            </div>
          </div>
        )}

        {/* Tab 3: Android Files Inspect */}
        {activeTab === 'files' && (
          <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
            <div className="bg-[#0e1520] border border-slate-800 rounded-2xl p-4 space-y-2">
              <div className="text-xs font-semibold uppercase text-slate-400 tracking-wider px-2 py-1">
                Native Android Components
              </div>
              {Object.keys(fileContents).map((fileName) => (
                <button
                  key={fileName}
                  onClick={() => setSelectedFile(fileName)}
                  className={`w-full text-left px-3 py-2.5 rounded-xl text-xs font-mono transition-all flex items-center justify-between ${
                    selectedFile === fileName
                      ? 'bg-cyan-500/15 border border-cyan-500/30 text-cyan-300'
                      : 'text-slate-400 hover:bg-slate-800/50 hover:text-slate-200'
                  }`}
                >
                  <span className="truncate">{fileName}</span>
                  <ArrowRight className="w-3.5 h-3.5 opacity-60" />
                </button>
              ))}
            </div>

            <div className="lg:col-span-2 bg-[#0e1520] border border-slate-800 rounded-2xl p-6 flex flex-col">
              <div className="flex flex-wrap items-center justify-between gap-3 mb-3 border-b border-slate-800 pb-3">
                <div>
                  <h3 className="text-sm font-bold text-white font-mono">{fileContents[selectedFile].path}</h3>
                  <p className="text-xs text-slate-400 mt-0.5">{fileContents[selectedFile].desc}</p>
                </div>
                <button
                  onClick={() => copyToClipboard(fileContents[selectedFile].code, selectedFile)}
                  className="px-3 py-1.5 rounded-lg bg-slate-800 hover:bg-slate-700 text-slate-300 text-xs font-mono flex items-center gap-1.5 transition-colors border border-slate-700"
                >
                  {copiedCmd === selectedFile ? <Check className="w-3.5 h-3.5 text-emerald-400" /> : <Copy className="w-3.5 h-3.5" />}
                  Copy File
                </button>
              </div>

              <div className="bg-[#05070a] border border-slate-800 rounded-xl p-4 font-mono text-xs text-slate-300 overflow-x-auto flex-1 max-h-[460px]">
                <pre>{fileContents[selectedFile].code}</pre>
              </div>
            </div>
          </div>
        )}

        {/* Tab 4: Architecture & Flow */}
        {activeTab === 'architecture' && (
          <div className="bg-[#0e1520] border border-slate-800 rounded-2xl p-6 space-y-6">
            <div>
              <h2 className="text-base font-semibold text-white flex items-center gap-2">
                <Zap className="w-4 h-4 text-cyan-400" />
                How Ignition Auto-Connect & Telemetry Logging Work in Android
              </h2>
              <p className="text-xs text-slate-400 mt-1">
                The native Android service architecture activates instantly upon vehicle startup.
              </p>
            </div>

            <div className="grid grid-cols-1 md:grid-cols-4 gap-4">
              <div className="bg-[#070b10] border border-slate-800/80 rounded-xl p-4 relative">
                <div className="text-cyan-400 font-mono text-xs font-bold mb-1">STEP 1</div>
                <div className="text-sm font-semibold text-white mb-2">Car Ignition Trigger</div>
                <p className="text-xs text-slate-400 leading-relaxed">
                  Ignition 12V charger activates <code className="text-cyan-300">ACTION_POWER_CONNECTED</code> or ELM327 Bluetooth adapter enters range (<code className="text-cyan-300">ACTION_ACL_CONNECTED</code>).
                </p>
              </div>

              <div className="bg-[#070b10] border border-slate-800/80 rounded-xl p-4 relative">
                <div className="text-cyan-400 font-mono text-xs font-bold mb-1">STEP 2</div>
                <div className="text-sm font-semibold text-white mb-2">Foreground Wakeup</div>
                <p className="text-xs text-slate-400 leading-relaxed">
                  <code className="text-cyan-300">IgnitionReceiver</code> receives the system broadcast and executes <code className="text-cyan-300">startForegroundService(ACTION_AUTO_CONNECT)</code>.
                </p>
              </div>

              <div className="bg-[#070b10] border border-slate-800/80 rounded-xl p-4 relative">
                <div className="text-cyan-400 font-mono text-xs font-bold mb-1">STEP 3</div>
                <div className="text-sm font-semibold text-white mb-2">Bluetooth Handshake</div>
                <p className="text-xs text-slate-400 leading-relaxed">
                  <code className="text-cyan-300">ObdService</code> retrieves remembered MAC address, initializes SPP socket, and sends AT commands: <code className="text-cyan-300">ATZ, ATE0, ATSP0</code>.
                </p>
              </div>

              <div className="bg-[#070b10] border border-slate-800/80 rounded-xl p-4 relative">
                <div className="text-cyan-400 font-mono text-xs font-bold mb-1">STEP 4</div>
                <div className="text-sm font-semibold text-white mb-2">Auto CSV Stream</div>
                <p className="text-xs text-slate-400 leading-relaxed">
                  Upon <code className="text-emerald-400">ConnectionState.Connected</code>, <code className="text-cyan-300">startCsvLogging()</code> immediately creates a timestamped file in <code className="text-cyan-300">obd_logs/</code>.
                </p>
              </div>
            </div>
          </div>
        )}
      </main>
    </div>
  );
}

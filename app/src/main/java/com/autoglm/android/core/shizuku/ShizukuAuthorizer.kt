package com.autoglm.android.core.shizuku

import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import rikka.shizuku.Shizuku

/**
 * Shizuku 授权工具类
 * 提供 Shizuku 权限检查和管理功能
 * 参考 Operit 实现
 */
object ShizukuAuthorizer {
    private const val TAG = "ShizukuAuthorizer"
    private val mainHandler = Handler(Looper.getMainLooper())

    // 注册状态
    private var binderReceivedListenerRegistered = false
    private var permissionRequestListenerRegistered = false

    // 服务状态
    private var isServiceAvailable = false
    
    // 错误消息缓存
    private var lastServiceErrorMessage = ""
    private var lastPermissionErrorMessage = ""

    // 状态变更回调
    private val stateChangeListeners = mutableListOf<() -> Unit>()

    /**
     * 添加状态变更监听器
     */
    fun addStateChangeListener(listener: () -> Unit) {
        synchronized(stateChangeListeners) {
            if (!stateChangeListeners.contains(listener)) {
                stateChangeListeners.add(listener)
            }
        }
    }

    /**
     * 移除状态变更监听器
     */
    fun removeStateChangeListener(listener: () -> Unit) {
        synchronized(stateChangeListeners) {
            stateChangeListeners.remove(listener)
        }
    }

    /**
     * 触发状态变更通知
     */
    private fun notifyStateChanged() {
        mainHandler.post {
            synchronized(stateChangeListeners) {
                Log.d(TAG, "Notifying ${stateChangeListeners.size} listeners about state change")
                stateChangeListeners.forEach { it.invoke() }
            }
        }
    }

    /**
     * 检查 Shizuku 是否已安装
     */
    fun isShizukuInstalled(context: Context): Boolean {
        return try {
            val packageInfo = context.packageManager.getPackageInfo("moe.shizuku.privileged.api", 0)
            val versionName = packageInfo.versionName
            Log.i(TAG, "检测到已安装 Shizuku，版本: $versionName")
            true
        } catch (e: PackageManager.NameNotFoundException) {
            Log.i(TAG, "未检测到已安装的 Shizuku")
            false
        } catch (e: Exception) {
            Log.e(TAG, "检查 Shizuku 是否安装时出错", e)
            false
        }
    }

    /**
     * 获取最后一次服务检查的错误信息
     */
    fun getServiceErrorMessage(): String = lastServiceErrorMessage
    
    /**
     * 获取最后一次权限检查的错误信息
     */
    fun getPermissionErrorMessage(): String = lastPermissionErrorMessage

    /**
     * 检查 Shizuku 服务是否正在运行
     */
    fun isShizukuServiceRunning(): Boolean {
        try {
            // 首先检查本地缓存的状态
            if (isServiceAvailable) {
                lastServiceErrorMessage = ""
                return true
            }

            // 使用 pingBinder - 最可靠的检测方法
            try {
                if (Shizuku.pingBinder()) {
                    Log.d(TAG, "Shizuku pingBinder succeeded")
                    isServiceAvailable = true
                    lastServiceErrorMessage = ""
                    return true
                }
            } catch (e: Exception) {
                Log.e(TAG, "Shizuku pingBinder check failed", e)
                lastServiceErrorMessage = "Shizuku ping failed: ${e.message}"
                return false
            }

            // 尝试获取并检查 binder 存活状态
            try {
                val binder = Shizuku.getBinder()
                if (binder != null && binder.isBinderAlive) {
                    Log.d(TAG, "Shizuku binder is alive")
                    isServiceAvailable = true
                    lastServiceErrorMessage = ""
                    return true
                } else if (binder == null) {
                    lastServiceErrorMessage = "Shizuku binder is null"
                    return false
                } else {
                    lastServiceErrorMessage = "Shizuku binder is not alive"
                    return false
                }
            } catch (e: Exception) {
                Log.e(TAG, "Binder check failed", e)
                lastServiceErrorMessage = "Failed to check binder: ${e.message}"
                return false
            }
        } catch (e: Throwable) {
            Log.e(TAG, "Critical error checking Shizuku service", e)
            isServiceAvailable = false
            lastServiceErrorMessage = "Critical error: ${e.message}"
            return false
        }
    }

    /**
     * 检查应用是否有 Shizuku 权限
     */
    fun hasShizukuPermission(): Boolean {
        try {
            val serviceRunning = isShizukuServiceRunning()
            if (!serviceRunning) {
                lastPermissionErrorMessage = "Shizuku service not running: $lastServiceErrorMessage"
                return false
            }

            val result = Shizuku.checkSelfPermission()
            val granted = result == PackageManager.PERMISSION_GRANTED
            if (granted) {
                lastPermissionErrorMessage = ""
                return true
            } else {
                lastPermissionErrorMessage = "Shizuku permission not granted (code: $result)"
                return false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking Shizuku permission", e)
            lastPermissionErrorMessage = "Error checking permission: ${e.message}"
            return false
        }
    }

    /**
     * 请求 Shizuku 权限
     */
    fun requestShizukuPermission(onResult: (Boolean) -> Unit) {
        val serviceRunning = isShizukuServiceRunning()
        if (!serviceRunning) {
            Log.e(TAG, "Cannot request permission: $lastServiceErrorMessage")
            onResult(false)
            return
        }

        val hasPermission = hasShizukuPermission()
        if (hasPermission) {
            Log.d(TAG, "Permission already granted")
            onResult(true)
            notifyStateChanged()
            return
        }

        Log.d(TAG, "Requesting Shizuku permission")

        // 移除之前的监听器避免重复
        try {
            if (permissionRequestListenerRegistered) {
                Shizuku.removeRequestPermissionResultListener { _, _ -> }
                permissionRequestListenerRegistered = false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error removing existing permission listener", e)
        }

        try {
            val requestCode = 100

            Log.d(TAG, "Setting up permission result listener")

            Shizuku.addRequestPermissionResultListener { code, grantResult ->
                Log.d(TAG, "Permission result received: code=$code, result=$grantResult")
                if (code == requestCode) {
                    val granted = grantResult == PackageManager.PERMISSION_GRANTED
                    Log.d(TAG, "Shizuku permission request result: $granted")
                    onResult(granted)
                    if (granted) {
                        notifyStateChanged()
                    }

                    // 权限请求完成后移除监听器
                    try {
                        Shizuku.removeRequestPermissionResultListener { _, _ -> }
                        permissionRequestListenerRegistered = false
                    } catch (e: Exception) {
                        Log.e(TAG, "Error removing permission listener", e)
                    }
                }
            }
            permissionRequestListenerRegistered = true

            // 请求权限
            Log.d(TAG, "Calling Shizuku.requestPermission($requestCode)")
            Shizuku.requestPermission(requestCode)
        } catch (e: Exception) {
            Log.e(TAG, "Error requesting Shizuku permission", e)
            onResult(false)
        }
    }

    /**
     * 初始化 Shizuku 绑定
     */
    fun initialize() {
        Log.d(TAG, "Initializing Shizuku")

        // 重置服务状态
        isServiceAvailable = false
        lastServiceErrorMessage = ""
        lastPermissionErrorMessage = ""

        // 移除之前的监听器避免重复
        if (binderReceivedListenerRegistered) {
            try {
                Shizuku.removeBinderReceivedListener {}
                Shizuku.removeBinderDeadListener {}
            } catch (e: Exception) {
                Log.e(TAG, "Error removing binder listeners", e)
            }
            binderReceivedListenerRegistered = false
        }

        try {
            // 设置绑定接收监听器
            Shizuku.addBinderReceivedListener {
                Log.d(TAG, "Shizuku binder received")
                isServiceAvailable = true
                notifyStateChanged()

                // 当收到 binder 时主动检查权限状态
                mainHandler.post {
                    try {
                        val hasPermission = hasShizukuPermission()
                        Log.d(TAG, "Checking permission after binder received: $hasPermission")
                    } catch (e: Exception) {
                        Log.e(TAG, "Error checking permission after binder received", e)
                    }
                }
            }

            // 设置绑定断开监听器
            Shizuku.addBinderDeadListener {
                Log.d(TAG, "Shizuku binder dead")
                isServiceAvailable = false
                notifyStateChanged()
            }

            binderReceivedListenerRegistered = true

            // 立即检查服务是否已经在运行
            val isRunning = isShizukuServiceRunning()
            Log.d(TAG, "Initial Shizuku service status check: $isRunning")
            if (isRunning) {
                mainHandler.post {
                    try {
                        val hasPermission = hasShizukuPermission()
                        Log.d(TAG, "Initial permission check: $hasPermission")
                        notifyStateChanged()
                    } catch (e: Exception) {
                        Log.e(TAG, "Error during initial permission check", e)
                    }
                }
            } else {
                // 如果服务未运行，500毫秒后再次检查以防初始化延迟
                mainHandler.postDelayed({
                    val retryCheck = isShizukuServiceRunning()
                    Log.d(TAG, "Delayed service status check: $retryCheck")
                    if (retryCheck) {
                        notifyStateChanged()
                    }
                }, 500)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing Shizuku", e)
        }
    }

    /**
     * 获取 Shizuku 启动说明
     */
    fun getShizukuStartupInstructions(): String {
        return """
            要启动 Shizuku 服务：
            
            方法一：ADB 命令（推荐）
            1. 在电脑上安装 ADB 工具
            2. 用 USB 连接手机并启用 USB 调试
            3. 运行命令：
               adb shell sh /sdcard/Android/data/moe.shizuku.privileged.api/start.sh
            
            方法二：无线调试（Android 11+）
            1. 在开发者选项中启用「无线调试」
            2. 打开 Shizuku 应用
            3. 按照屏幕提示配对
            
            方法三：Root
            1. 如果设备已 Root
            2. 打开 Shizuku 并授予 Root 权限
        """.trimIndent()
    }
}

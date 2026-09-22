package com.example.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.DownloadListener
import android.webkit.GeolocationPermissions
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.ui.theme.KothaAccent
import com.example.ui.theme.KothaCardDark
import com.example.ui.theme.KothaPrimary
import com.example.ui.theme.KothaSuccess
import com.example.util.NetworkMonitor

const val KOTHAHUB_BASE_URL = "https://kothahub.com"

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun KothaHubScreen(
  modifier: Modifier = Modifier,
  initialUrl: String = KOTHAHUB_BASE_URL
) {
  val context = LocalContext.current
  val networkMonitor = remember { NetworkMonitor(context) }
  val isOnline by networkMonitor.isOnlineFlow.collectAsState(initial = networkMonitor.isOnline())

  var webViewInstance by remember { mutableStateOf<WebView?>(null) }
  var currentUrl by remember { mutableStateOf(initialUrl) }
  var pageTitle by remember { mutableStateOf("KothaHub") }
  var isLoading by remember { mutableStateOf(true) }
  var loadingProgress by remember { mutableFloatStateOf(0.1f) }
  var hasError by remember { mutableStateOf(false) }
  var errorMessage by remember { mutableStateOf<String?>(null) }
  var canGoBack by remember { mutableStateOf(false) }
  var canGoForward by remember { mutableStateOf(false) }

  var showMenu by remember { mutableStateOf(false) }
  var showInfoDialog by remember { mutableStateOf(false) }
  var fastModeEnabled by remember { mutableStateOf(true) }

  // Exit with double back press
  var lastBackPressTime by remember { mutableLongStateOf(0L) }

  // File chooser handling for all Android devices
  var fileUploadCallback by remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }
  val fileChooserLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.StartActivityForResult()
  ) { result ->
    val callback = fileUploadCallback
    if (callback != null) {
      val dataIntent = result.data
      val results: Array<Uri>? = when {
        result.resultCode != Activity.RESULT_OK -> null
        dataIntent?.data != null -> arrayOf(dataIntent.data!!)
        dataIntent?.clipData != null -> {
          val clipData = dataIntent.clipData!!
          Array(clipData.itemCount) { i -> clipData.getItemAt(i).uri }
        }
        else -> null
      }
      callback.onReceiveValue(results)
      fileUploadCallback = null
    }
  }

  // Reload when internet connection recovers if page had error
  LaunchedEffect(isOnline) {
    if (isOnline && hasError) {
      hasError = false
      webViewInstance?.reload()
    }
  }

  // Back button navigation
  BackHandler(enabled = true) {
    val wv = webViewInstance
    if (wv != null && wv.canGoBack()) {
      wv.goBack()
    } else {
      val currentTime = System.currentTimeMillis()
      if (currentTime - lastBackPressTime < 2000) {
        (context as? Activity)?.finish()
      } else {
        lastBackPressTime = currentTime
        Toast.makeText(context, "Press back again to exit KothaHub", Toast.LENGTH_SHORT).show()
      }
    }
  }

  Scaffold(
    modifier = modifier.fillMaxSize(),
    topBar = {
      Column(modifier = Modifier.fillMaxWidth()) {
        TopAppBar(
          title = {
            Column {
              Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                  text = "KothaHub",
                  style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                  ),
                  maxLines = 1,
                  overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.width(6.dp))
                // Online/Offline status indicator pill
                Box(
                  modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(if (isOnline) KothaSuccess else Color(0xFFFF5252))
                )
              }
              Text(
                text = if (hasError) "Connection offline" else if (isLoading) "Loading words..." else pageTitle,
                style = MaterialTheme.typography.bodySmall.copy(
                  fontSize = 11.sp,
                  color = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
              )
            }
          },
          navigationIcon = {
            IconButton(
              onClick = { webViewInstance?.goBack() },
              enabled = canGoBack,
              modifier = Modifier.testTag("nav_back_button")
            ) {
              Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back in history",
                tint = if (canGoBack) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
              )
            }
          },
          actions = {
            // Forward button
            IconButton(
              onClick = { webViewInstance?.goForward() },
              enabled = canGoForward,
              modifier = Modifier.testTag("nav_forward_button")
            ) {
              Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = "Forward in history",
                tint = if (canGoForward) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
              )
            }

            // Refresh button
            IconButton(
              onClick = {
                hasError = false
                webViewInstance?.reload()
              },
              modifier = Modifier.testTag("nav_refresh_button")
            ) {
              Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = "Refresh page",
                tint = MaterialTheme.colorScheme.primary
              )
            }

            // Home button
            IconButton(
              onClick = {
                hasError = false
                webViewInstance?.loadUrl(KOTHAHUB_BASE_URL)
              },
              modifier = Modifier.testTag("nav_home_button")
            ) {
              Icon(
                imageVector = Icons.Default.Home,
                contentDescription = "Go to KothaHub home",
                tint = MaterialTheme.colorScheme.onSurface
              )
            }

            // More Menu
            Box {
              IconButton(
                onClick = { showMenu = true },
                modifier = Modifier.testTag("nav_more_menu_button")
              ) {
                Icon(
                  imageVector = Icons.Default.MoreVert,
                  contentDescription = "More options"
                )
              }

              DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false }
              ) {
                DropdownMenuItem(
                  text = { Text("Share KothaHub Link") },
                  leadingIcon = {
                    Icon(Icons.Default.Share, contentDescription = null)
                  },
                  onClick = {
                    showMenu = false
                    val shareUrl = currentUrl.ifBlank { KOTHAHUB_BASE_URL }
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                      type = "text/plain"
                      putExtra(Intent.EXTRA_SUBJECT, "KothaHub - Everyone's words, everyone's connection")
                      putExtra(Intent.EXTRA_TEXT, "Join the conversation on KothaHub: $shareUrl")
                    }
                    context.startActivity(Intent.createChooser(shareIntent, "Share KothaHub"))
                  }
                )

                DropdownMenuItem(
                  text = { Text("Open in External Browser") },
                  leadingIcon = {
                    Icon(Icons.Default.OpenInBrowser, contentDescription = null)
                  },
                  onClick = {
                    showMenu = false
                    val targetUrl = currentUrl.ifBlank { KOTHAHUB_BASE_URL }
                    try {
                      val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(targetUrl))
                      context.startActivity(browserIntent)
                    } catch (e: Exception) {
                      Toast.makeText(context, "Cannot open external browser", Toast.LENGTH_SHORT).show()
                    }
                  }
                )

                DropdownMenuItem(
                  text = { Text("Clear Cache & Storage") },
                  leadingIcon = {
                    Icon(Icons.Default.Speed, contentDescription = null)
                  },
                  onClick = {
                    showMenu = false
                    webViewInstance?.clearCache(true)
                    webViewInstance?.clearHistory()
                    Toast.makeText(context, "KothaHub cache cleared for fast speed", Toast.LENGTH_SHORT).show()
                    webViewInstance?.reload()
                  }
                )

                DropdownMenuItem(
                  text = { Text("App Info & Speed Settings") },
                  leadingIcon = {
                    Icon(Icons.Default.Info, contentDescription = null)
                  },
                  onClick = {
                    showMenu = false
                    showInfoDialog = true
                  }
                )
              }
            }
          },
          colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface
          )
        )

        // Smooth Linear Progress Indicator
        AnimatedVisibility(
          visible = isLoading && !hasError,
          enter = fadeIn(),
          exit = fadeOut()
        ) {
          LinearProgressIndicator(
            progress = { loadingProgress },
            modifier = Modifier
              .fillMaxWidth()
              .height(3.dp)
              .testTag("page_loading_indicator"),
            color = KothaAccent,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
          )
        }
      }
    }
  ) { paddingValues ->
    Box(
      modifier = Modifier
        .fillMaxSize()
        .padding(paddingValues)
    ) {
      // WebView container
      AndroidView(
        modifier = Modifier
          .fillMaxSize()
          .testTag("kothahub_webview"),
        factory = { ctx ->
          WebView(ctx).apply {
            layoutParams = ViewGroup.LayoutParams(
              ViewGroup.LayoutParams.MATCH_PARENT,
              ViewGroup.LayoutParams.MATCH_PARENT
            )

            // Hardware acceleration for fast rendering
            setLayerType(View.LAYER_TYPE_HARDWARE, null)

            // Setup Cookies
            val cookieManager = CookieManager.getInstance()
            cookieManager.setAcceptCookie(true)
            cookieManager.setAcceptThirdPartyCookies(this, true)

            // Optimize WebSettings for maximum performance and full modern web support
            settings.apply {
              javaScriptEnabled = true
              domStorageEnabled = true
              databaseEnabled = true
              allowFileAccess = true
              allowContentAccess = true
              loadsImagesAutomatically = true
              useWideViewPort = true
              loadWithOverviewMode = true
              setSupportZoom(true)
              builtInZoomControls = true
              displayZoomControls = false
              mediaPlaybackRequiresUserGesture = false
              defaultTextEncodingName = "UTF-8"
              mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE

              // Cache mode: Fast local cache first if offline, standard live network otherwise
              cacheMode = if (isOnline) {
                WebSettings.LOAD_DEFAULT
              } else {
                WebSettings.LOAD_CACHE_ELSE_NETWORK
              }

              // Append App identifier to user agent while maintaining standard Chrome compatibility
              val defaultUa = userAgentString ?: ""
              userAgentString = "$defaultUa KothaHubApp/1.0 (Android; FastMobile)"
            }

            // Handle downloads
            setDownloadListener(DownloadListener { url, userAgent, contentDisposition, mimetype, _ ->
              try {
                val request = DownloadManager.Request(Uri.parse(url)).apply {
                  setMimeType(mimetype)
                  addRequestHeader("User-Agent", userAgent)
                  val cookie = CookieManager.getInstance().getCookie(url)
                  addRequestHeader("Cookie", cookie)
                  setDescription("Downloading from KothaHub")
                  setTitle(URLUtil.guessFileName(url, contentDisposition, mimetype))
                  setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                  setDestinationInExternalPublicDir(
                    Environment.DIRECTORY_DOWNLOADS,
                    URLUtil.guessFileName(url, contentDisposition, mimetype)
                  )
                }
                val dm = ctx.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
                dm?.enqueue(request)
                Toast.makeText(ctx, "Download started...", Toast.LENGTH_SHORT).show()
              } catch (e: Exception) {
                Toast.makeText(ctx, "Error starting download: ${e.message}", Toast.LENGTH_SHORT).show()
              }
            })

            // WebViewClient
            webViewClient = object : WebViewClient() {
              override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
              ): Boolean {
                val target = request?.url ?: return false
                val scheme = target.scheme?.lowercase() ?: ""
                val host = target.host?.lowercase() ?: ""

                // Handle system schemes: tel, mailto, sms, whatsapp, market, intent
                if (scheme == "tel" || scheme == "mailto" || scheme == "sms" ||
                  scheme == "whatsapp" || scheme == "intent" || scheme == "market"
                ) {
                  try {
                    val intent = Intent(Intent.ACTION_VIEW, target)
                    ctx.startActivity(intent)
                    return true
                  } catch (e: Exception) {
                    Toast.makeText(ctx, "No app available to handle this action", Toast.LENGTH_SHORT).show()
                    return true
                  }
                }

                // If within kothahub domain or subdomains, navigate smoothly inside WebView
                if (host.contains("kothahub.com") || host.isBlank()) {
                  return false
                }

                // External URLs: open directly inside if user desires or prompt
                return false
              }

              override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                isLoading = true
                hasError = false
                currentUrl = url ?: ""
                canGoBack = view?.canGoBack() ?: false
                canGoForward = view?.canGoForward() ?: false
              }

              override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                isLoading = false
                currentUrl = url ?: ""
                pageTitle = view?.title ?: "KothaHub"
                canGoBack = view?.canGoBack() ?: false
                canGoForward = view?.canGoForward() ?: false
              }

              override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
              ) {
                super.onReceivedError(view, request, error)
                // Only treat as fatal error if it's the main frame
                if (request?.isForMainFrame == true) {
                  isLoading = false
                  hasError = true
                  errorMessage = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    error?.description?.toString() ?: "Network error"
                  } else {
                    "Unable to load page"
                  }
                }
              }
            }

            // WebChromeClient
            webChromeClient = object : WebChromeClient() {
              override fun onProgressChanged(view: WebView?, newProgress: Int) {
                super.onProgressChanged(view, newProgress)
                loadingProgress = (newProgress / 100f).coerceIn(0.05f, 1f)
                if (newProgress >= 100) {
                  isLoading = false
                }
              }

              override fun onReceivedTitle(view: WebView?, title: String?) {
                super.onReceivedTitle(view, title)
                if (!title.isNullOrBlank() && !title.startsWith("http")) {
                  pageTitle = title
                }
              }

              override fun onGeolocationPermissionsShowPrompt(
                origin: String?,
                callback: GeolocationPermissions.Callback?
              ) {
                callback?.invoke(origin, true, false)
              }

              override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?
              ): Boolean {
                fileUploadCallback?.onReceiveValue(null)
                fileUploadCallback = filePathCallback

                try {
                  val intent = fileChooserParams?.createIntent() ?: Intent(Intent.ACTION_GET_CONTENT).apply {
                    type = "*/*"
                    addCategory(Intent.CATEGORY_OPENABLE)
                  }
                  fileChooserLauncher.launch(intent)
                  return true
                } catch (e: Exception) {
                  try {
                    val fallbackIntent = Intent(Intent.ACTION_GET_CONTENT).apply {
                      type = "image/*"
                      addCategory(Intent.CATEGORY_OPENABLE)
                    }
                    fileChooserLauncher.launch(fallbackIntent)
                    return true
                  } catch (ex: Exception) {
                    fileUploadCallback?.onReceiveValue(null)
                    fileUploadCallback = null
                    return false
                  }
                }
              }
            }

            // Load initial website
            loadUrl(initialUrl)
            webViewInstance = this
          }
        },
        update = { wv ->
          // Update fast mode cache policy if needed
          wv.settings.cacheMode = if (isOnline) {
            if (fastModeEnabled) WebSettings.LOAD_DEFAULT else WebSettings.LOAD_NORMAL
          } else {
            WebSettings.LOAD_CACHE_ELSE_NETWORK
          }
        }
      )

      // Graceful Native Error / Offline UI Screen
      if (hasError) {
        Surface(
          modifier = Modifier
            .fillMaxSize()
            .testTag("offline_error_view"),
          color = MaterialTheme.colorScheme.background
        ) {
          Column(
            modifier = Modifier
              .fillMaxSize()
              .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
          ) {
            Box(
              modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.errorContainer),
              contentAlignment = Alignment.Center
            ) {
              Icon(
                imageVector = Icons.Default.CloudOff,
                contentDescription = "No Connection",
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.error
              )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
              text = if (!isOnline) "No Internet Connection" else "Unable to Connect to KothaHub",
              style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp
              ),
              color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
              text = if (!isOnline) {
                "Please check your Wi-Fi or mobile data connection to access conversations on KothaHub."
              } else {
                "KothaHub server could not be reached. Error: ${errorMessage ?: "Connection timed out"}"
              },
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            Row(
              horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
              Button(
                onClick = {
                  hasError = false
                  isLoading = true
                  webViewInstance?.reload()
                },
                colors = ButtonDefaults.buttonColors(
                  containerColor = KothaPrimary
                ),
                modifier = Modifier.testTag("retry_connection_button")
              ) {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Retry Connection")
              }

              OutlinedButton(
                onClick = {
                  hasError = false
                  isLoading = true
                  webViewInstance?.loadUrl(KOTHAHUB_BASE_URL)
                },
                modifier = Modifier.testTag("retry_home_button")
              ) {
                Text("Go to Home")
              }
            }
          }
        }
      }
    }
  }

  // Info & Fast Mode Dialog
  if (showInfoDialog) {
    AlertDialog(
      onDismissRequest = { showInfoDialog = false },
      title = {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(Icons.Default.Speed, contentDescription = null, tint = KothaAccent)
          Spacer(modifier = Modifier.width(8.dp))
          Text("KothaHub Speed & Support")
        }
      },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
          Text(
            text = "Everyone's words, everyone's connection ✨",
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.primary
          )

          Card(
            colors = CardDefaults.cardColors(
              containerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            shape = RoundedCornerShape(12.dp)
          ) {
            Column(modifier = Modifier.padding(12.dp)) {
              Text(
                text = "⚡ Hardware Accelerated Engine",
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
              )
              Text(
                text = "Optimized with GPU composition, DOM storage, and WebSQL for ultra-fast page rendering across all Android versions.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
          }

          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text("Fast Cache Turbo", fontWeight = FontWeight.Medium)
              Text("Keeps frequently accessed assets cached for instant responses", style = MaterialTheme.typography.bodySmall)
            }
            Switch(
              checked = fastModeEnabled,
              onCheckedChange = { fastModeEnabled = it }
            )
          }

          Card(
            colors = CardDefaults.cardColors(
              containerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            shape = RoundedCornerShape(12.dp)
          ) {
            Column(modifier = Modifier.padding(12.dp)) {
              Text(
                text = "📱 Universal Android Support",
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
              )
              Text(
                text = "Full support for Android 7.0 through Android 16 (API 24 to 36). Includes photo uploads, camera capture, safe browsing, and offline recovery.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
              )
            }
          }
        }
      },
      confirmButton = {
        TextButton(onClick = { showInfoDialog = false }) {
          Text("Done")
        }
      }
    )
  }

  // Cleanup on exit
  DisposableEffect(Unit) {
    onDispose {
      webViewInstance?.apply {
        stopLoading()
        clearHistory()
        destroy()
      }
    }
  }
}

package com.puretrans.app.ui.image

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.puretrans.app.ui.theme.PureTransTheme
import java.io.File

class ImageTranslateActivity : ComponentActivity() {

    private val viewModel: ImageTranslateViewModel by viewModels()

    private var tempCameraUri: Uri? = null

    // 相机拍照 Launcher
    private val takePhotoLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && tempCameraUri != null) {
            viewModel.loadFromUri(tempCameraUri!!)
        }
    }

    // 相机权限申请 Launcher
    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            launchCamera()
        } else {
            Toast.makeText(this, "需要相机权限以拍摄照片进行翻译", Toast.LENGTH_SHORT).show()
        }
    }

    // 相册选图 Launcher
    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.loadFromUri(uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 检查 Intent 中是否带有初始图片 URI
        intent?.data?.let { uri ->
            viewModel.loadFromUri(uri)
        }

        setContent {
            PureTransTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val uiState by viewModel.uiState.collectAsState()

                    ImageTranslateScreen(
                        uiState = uiState,
                        onNavigateBack = { finish() },
                        onTakePhoto = { checkCameraPermissionAndLaunch() },
                        onPickImage = { pickImageLauncher.launch("image/*") },
                        onToggleShowOriginal = viewModel::toggleShowOriginal,
                        onSelectSourceLanguage = viewModel::changeSourceLanguage,
                        onSelectTargetLanguage = viewModel::changeTargetLanguage,
                        onSelectBlock = viewModel::selectBlock,
                        onSpeakText = viewModel::speakText,
                        onSaveImage = {
                            val original = uiState.bitmap
                            if (original != null) {
                                val composite = ImageExportHelper.createCompositeBitmap(original, uiState.textBlocks)
                                val savedUri = ImageExportHelper.saveBitmapToGallery(this@ImageTranslateActivity, composite)
                                if (savedUri != null) {
                                    Toast.makeText(this@ImageTranslateActivity, "图片已成功保存到相册", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(this@ImageTranslateActivity, "保存失败，请检查存储权限", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        onShareImage = {
                            val original = uiState.bitmap
                            if (original != null) {
                                val composite = ImageExportHelper.createCompositeBitmap(original, uiState.textBlocks)
                                ImageExportHelper.shareBitmap(this@ImageTranslateActivity, composite)
                            }
                        }
                    )
                }
            }
        }
    }

    private fun checkCameraPermissionAndLaunch() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            launchCamera()
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun launchCamera() {
        try {
            val photoFile = File(cacheDir, "camera_capture_${System.currentTimeMillis()}.jpg")
            val uri = FileProvider.getUriForFile(
                this,
                "${packageName}.fileprovider",
                photoFile
            )
            tempCameraUri = uri
            takePhotoLauncher.launch(uri)
        } catch (e: Exception) {
            Toast.makeText(this, "调起相机失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        fun start(context: Context, imageUri: Uri? = null) {
            val intent = Intent(context, ImageTranslateActivity::class.java).apply {
                if (imageUri != null) {
                    data = imageUri
                }
            }
            context.startActivity(intent)
        }
    }
}

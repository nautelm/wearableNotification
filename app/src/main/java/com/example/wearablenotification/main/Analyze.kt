package com.example.android.camera.utils.com.example.trafficlightdetection

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.Rect
import android.graphics.RectF
import android.media.Image
import android.util.Log
import android.util.Size
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.example.wearablenotification.main.ObjectDetector
import com.example.wearablenotification.main.OverlaySurfaceView
import com.example.wearablenotification.main.RoiCalculator
import org.tensorflow.lite.Interpreter

/**
 * Analyze内の画像解析ユースケース
 * @param yuvToRgbConverter カメラ画像のImageバッファYUV_420_888からRGB形式に変換する
 * @param interpreter tfliteモデルを操作するライブラリ
 * @param labels 正解ラベルのリスト
 * @param overlaySurfaceView Surface Viewのコールバック
 * @param resultViewSize プレビューのサイズ
 */

class Analyze(
    private val yuvToRgbConverter: YuvToRgbConverter,
    private val interpreter: Interpreter,
    private val labels: List<String>,
    private var overlaySurfaceView: OverlaySurfaceView,
    private val resultViewSize: Size
) : ImageAnalysis.Analyzer {

    private var imageRotationDegrees: Int = 0

    @SuppressLint("UnsafeExperimentalUsageError")
    override fun analyze(imageProxy: ImageProxy) {
        if (imageProxy.image == null) return

        // TODO : 交差点接近

        // TODO : 車速の取得

        //取得画像の回転向き、大きさを取得
        imageRotationDegrees = imageProxy.imageInfo.rotationDegrees
        val imageProxySize = Size(imageProxy.width, imageProxy.height)

        // 物体検知器の作成
        val objectDetector = ObjectDetector(interpreter, labels, imageRotationDegrees)

        // TODO : ROI自動化
        val roi = RoiCalculator().calcRoi(imageProxySize)

        // 解析対象の画像を取得 (YUV -> RGB bitmap -> ROIで切り取る)
        // bitmap : カメラ原画像
        // roiBitmap : ROI画像
        val bitmap = yuvToRgbBitmap(imageProxy.image!!)
        val roiBitmap = cropBitmap(roi, bitmap)

        imageProxy.close() // imageProxyの解放 : 必ず呼ぶ

        // 信号機検知処理(推論処理)
        // detectedObjectList : roiBitmap座標
        // 確率の高い順に格納されている
        val detectedObjectList = objectDetector.detect(roiBitmap)

        // 赤信号フラグ
        var redIsLighting = false

        // 信号機色判定処理(検知された場合のみ実行)
        if(detectedObjectList.isNotEmpty()) {
            // 最も確率の高い部分のみ抜き出す
            val trafficLightBitmap = cropBitmap(detectedObjectList[0].boundingBox, roiBitmap)
            redIsLighting = objectDetector.analyzeTrafficColor(trafficLightBitmap)
        }

        // TODO : 警告通知処理 (speedで分岐)

        // 検出結果の表示(OverlaySurfaceView.kt参照)
        overlaySurfaceView.draw(roi, detectedObjectList, redIsLighting, imageProxySize, resultViewSize)

        // TODO : interpreterのリソースの解放(Analyze外)
        // interpreter.close()
    }


    // 画像をYUV -> RGB bitmap に変換する
    private fun yuvToRgbBitmap(targetImage: Image): Bitmap {

        // YUVの生成
        val targetBitmap =
            Bitmap.createBitmap(targetImage.width, targetImage.height, Bitmap.Config.ARGB_8888)

        // RGB bitmapに変換
        yuvToRgbConverter.yuvToRgb(targetImage, targetBitmap)

        return targetBitmap
    }

    // ROIで切り取る
    private fun cropBitmap(roi: RectF, targetBitmap: Bitmap): Bitmap {

        Log.d("Debug", "roi : " + roi)

        // 型、条件に合うように整形
        val tmpRoi = Rect(
            largerValue(roi.left, 0f).toInt(),
            largerValue(roi.top, 0f).toInt(),
            smallerValue(roi.right, targetBitmap.width.toFloat()).toInt(),
            smallerValue(roi.bottom, targetBitmap.height.toFloat()).toInt()
        )

        // ROIの領域を切り取る(ImageProxy座標)
        return Bitmap.createBitmap(
            targetBitmap,
            tmpRoi.left,
            tmpRoi.top,
            tmpRoi.right - tmpRoi.left,
            tmpRoi.bottom - tmpRoi.top,
            null,
            true
        )
    }

    private fun smallerValue(x : Float, y: Float) : Float{
        if(x < y){
            return x
        }else{
            return y
        }
    }

    private fun largerValue(x : Float, y: Float) : Float {
        if (x > y) {
            return x
        } else {
            return y
        }
    }
}
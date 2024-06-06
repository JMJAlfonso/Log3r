package com.example.myapplication.activity

import android.Manifest
import android.annotation.SuppressLint
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.YuvImage
import android.media.Image
import android.os.Bundle
import android.text.InputType
import android.util.Log
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.AspectRatio
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.myapplication.R
import com.example.myapplication.model.EmbeddingsResponse
import com.example.myapplication.model.ImagenModel
import com.example.myapplication.service.FaceRecognition
import com.example.myapplication.service.RetrofitClient
import com.example.myapplication.utils.GraphicOverlay
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.io.ByteArrayOutputStream
import java.nio.ReadOnlyBufferException
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import kotlin.experimental.inv

class CameraxAddFaceActivity : AppCompatActivity() {
    private var previewView: PreviewView? = null
    private var cameraSelector: CameraSelector? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var lensFacing = CameraSelector.LENS_FACING_FRONT
    private var previewUseCase: Preview? = null
    private var analysisUseCase: ImageAnalysis? = null
    private var graphicOverlay: GraphicOverlay? = null
    private var previewImg: ImageView? = null
    private var detectionTextView: TextView? = null

    private var flipX = false
    private var start = true
    private var embeddings: FloatArray? = null

    private var faceRecognition: FaceRecognition? = null

    private var loading = false
    private var userId: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.camerax_add_face)
        previewView = findViewById(R.id.previewView)
        previewView?.scaleType = PreviewView.ScaleType.FIT_CENTER
        graphicOverlay = findViewById(R.id.graphic_overlay)
        previewImg = findViewById(R.id.preview_img)
        detectionTextView = findViewById(R.id.detection_text)

//        val addBtn = findViewById<ImageButton>(R.id.add_btn)
//        addBtn.setOnClickListener((View.OnClickListener { v: View? -> addFace() }))
        val switchCamBtn = findViewById<ImageButton>(R.id.switch_camera)
        switchCamBtn.setOnClickListener((View.OnClickListener { view: View? -> switchCamera() }))

        userId = intent.getStringExtra("userId").toString()
        //        loadModel();
        faceRecognition = FaceRecognition()
    }

    fun onAddButtonClick(view: View){
        if(embeddings != null){
            val imgModel = ImagenModel("", embeddings!!, userId)
            RetrofitClient.imagenApiService.postImagenes(imgModel).enqueue(object :
                Callback<ImagenModel> {
                override fun onResponse(call: Call<ImagenModel>, response: Response<ImagenModel>) {
                    if (response.isSuccessful) {
                        println("Embeddings sent successfully")
                        Toast.makeText(this@CameraxAddFaceActivity, "Se registro la imagen exitosamente.", Toast.LENGTH_SHORT).show()
                        val intent = Intent(applicationContext, ModificacionUsuarioActivity::class.java)
                        startActivity(intent)

                    } else {
                        println("Failed to send Embeddings: ${response.code()}")
                        Toast.makeText(this@CameraxAddFaceActivity, "No se pudo registrar la imagen.", Toast.LENGTH_SHORT).show()
                    }
                }

                override fun onFailure(call: Call<ImagenModel>, t: Throwable) {
                    println("Error sending Embeddings: ${t.message}")
                }
            })
        }

    }

    override fun onResume() {
        super.onResume()
        startCamera()
    }

    private val permissions: Unit
        /** Permissions Handler  */
        get() {
            ActivityCompat.requestPermissions(this, arrayOf(CAMERA_PERMISSION), PERMISSION_CODE)
        }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        for (r in grantResults) {
            if (r == PackageManager.PERMISSION_DENIED) {
                Toast.makeText(this, "Permission Denied", Toast.LENGTH_SHORT).show()
                return
            }
        }

        if (requestCode == PERMISSION_CODE) {
            setupCamera()
        }

        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    /** Setup camera & use cases  */
    private fun startCamera() {
        if (ContextCompat.checkSelfPermission(
                this,
                CAMERA_PERMISSION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            setupCamera()
        } else {
            permissions
        }
    }

    private fun setupCamera() {
        val cameraProviderFuture =
            ProcessCameraProvider.getInstance(this)

        cameraSelector = CameraSelector.Builder().requireLensFacing(lensFacing).build()

        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()
                bindAllCameraUseCases()
            } catch (e: ExecutionException) {
                Log.e(TAG, "cameraProviderFuture.addListener Error", e)
            } catch (e: InterruptedException) {
                Log.e(TAG, "cameraProviderFuture.addListener Error", e)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun bindAllCameraUseCases() {
        if (cameraProvider != null) {
            cameraProvider!!.unbindAll()
            bindPreviewUseCase()
            bindAnalysisUseCase()
        }
    }

    private fun bindPreviewUseCase() {
        if (cameraProvider == null) {
            return
        }

        if (previewUseCase != null) {
            cameraProvider!!.unbind(previewUseCase)
        }

        val builder = Preview.Builder()
        builder.setTargetAspectRatio(AspectRatio.RATIO_4_3)
        builder.setTargetRotation(rotation)

        previewUseCase = builder.build()
        previewUseCase!!.setSurfaceProvider(previewView!!.surfaceProvider)

        try {
            cameraProvider!!
                .bindToLifecycle(this, cameraSelector!!, previewUseCase)
        } catch (e: Exception) {
            Log.e(TAG, "Error when bind preview", e)
        }
    }

    private fun bindAnalysisUseCase() {
        if (cameraProvider == null) {
            return
        }

        if (analysisUseCase != null) {
            cameraProvider!!.unbind(analysisUseCase)
        }

        //creates a thread to analyze frames
        val cameraExecutor: Executor = Executors.newSingleThreadExecutor()

        val builder = ImageAnalysis.Builder()
        builder.setTargetAspectRatio(AspectRatio.RATIO_4_3)
        builder.setTargetRotation(rotation)

        //        ImageAnalysis anal = new ImageAnalysis.Analyzer()
        analysisUseCase = builder.build()
        //The analyzer will signal that the camera should begin sending data
        analysisUseCase!!.setAnalyzer(cameraExecutor) { image: ImageProxy -> this.analyze(image) }

        try {
            cameraProvider!!
                .bindToLifecycle(this, cameraSelector!!, analysisUseCase)
        } catch (e: Exception) {
            Log.e(TAG, "Error when bind analysis", e)
        }
    }

    @get:Throws(NullPointerException::class)
    protected val rotation: Int
        get() = previewView!!.display.rotation

    private fun switchCamera() {
        if (lensFacing == CameraSelector.LENS_FACING_BACK) {
            lensFacing = CameraSelector.LENS_FACING_FRONT
            flipX = true
        } else {
            lensFacing = CameraSelector.LENS_FACING_BACK
            flipX = false
        }

        if (cameraProvider != null) cameraProvider!!.unbindAll()
        startCamera()
    }

    /** Face detection processor  */
    @SuppressLint("UnsafeOptInUsageError")
    private fun analyze(image: ImageProxy) {
        if (image.image == null) return

        val inputImage = InputImage.fromMediaImage(
            image.image!!,
            image.imageInfo.rotationDegrees
        )

        val faceDetector = FaceDetection.getClient()


        faceDetector.process(inputImage)
            .addOnSuccessListener { faces: List<Face> -> onSuccessListener(faces, inputImage) }
            .addOnFailureListener { e: Exception? -> Log.e(TAG, "Barcode process failure", e) }
            .addOnCompleteListener { task: Task<List<Face?>?>? -> image.close() }
    }

    private fun onSuccessListener(faces: List<Face>, inputImage: InputImage) {
        var boundingBox: Rect? = null
        var name: String? = null
        val scaleX = previewView!!.width.toFloat() / inputImage.height.toFloat()
        val scaleY = previewView!!.height.toFloat() / inputImage.width.toFloat()

        if (faces.size > 0) {
            detectionTextView!!.setText(R.string.face_detected)
            // get first face detected
            val face = faces[0]

            // get bounding box of face;
            boundingBox = face.boundingBox

            // convert img to bitmap & crop img
            val bitmap = mediaImgToBmp(
                inputImage.mediaImage,
                inputImage.rotationDegrees,
                boundingBox
            )

            //            if(start) name = recognizeImage(bitmap);
            embeddings = faceRecognition!!.getFaceEmbeddings(bitmap, this)

            detectionTextView!!.text = embeddings.toString()
        } else {
            detectionTextView!!.setText(R.string.no_face_detected)
        }

//        graphicOverlay!!.draw(boundingBox, scaleX, scaleY, name)
    }

    /** Recognize Processor  */
    private fun addFace() {
        start = false
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Enter Name")

        // Set up the input
        val input = EditText(this)

        input.inputType = InputType.TYPE_CLASS_TEXT
        input.maxWidth = 200
        builder.setView(input)

        // Set up the buttons
        builder.setPositiveButton("ADD") { dialog: DialogInterface?, which: Int ->

            faceRecognition!!.addEmbedding(embeddings!!, input.text.toString())
            start = true
        }
        builder.setNegativeButton("Cancel") { dialog: DialogInterface, which: Int ->
            start = true
            dialog.cancel()
        }

        builder.show()
    }


    /** Bitmap Converter  */
    private fun mediaImgToBmp(image: Image?, rotation: Int, boundingBox: Rect): Bitmap {
        //Convert media image to Bitmap
        val frame_bmp = toBitmap(image)

        //Adjust orientation of Face
        val frame_bmp1 = rotateBitmap(frame_bmp, rotation, flipX)

        //Crop out bounding box from whole Bitmap(image)
        val padding = 0.0f
        val adjustedBoundingBox = RectF(
            boundingBox.left - padding,
            boundingBox.top - padding,
            boundingBox.right + padding,
            boundingBox.bottom + padding
        )
        val cropped_face = getCropBitmapByCPU(frame_bmp1, adjustedBoundingBox)

        //Resize bitmap to 112,112
        return getResizedBitmap(cropped_face)
    }

    private fun getResizedBitmap(bm: Bitmap): Bitmap {
        val width = bm.width
        val height = bm.height
        val scaleWidth = (112f) / width
        val scaleHeight = (112f) / height
        // CREATE A MATRIX FOR THE MANIPULATION
        val matrix = Matrix()
        // RESIZE THE BIT MAP
        matrix.postScale(scaleWidth, scaleHeight)

        // "RECREATE" THE NEW BITMAP
        val resizedBitmap = Bitmap.createBitmap(
            bm, 0, 0, width, height, matrix, false
        )
        bm.recycle()
        return resizedBitmap
    }

    private fun toBitmap(image: Image?): Bitmap {
        val nv21 = YUV_420_888toNV21(image)


        val yuvImage = YuvImage(nv21, ImageFormat.NV21, image!!.width, image.height, null)

        val out = ByteArrayOutputStream()
        yuvImage.compressToJpeg(Rect(0, 0, yuvImage.width, yuvImage.height), 75, out)

        val imageBytes = out.toByteArray()

        return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
    }


    companion object {
        private const val TAG = "MainActivity"
        private const val PERMISSION_CODE = 1001
        private const val CAMERA_PERMISSION = Manifest.permission.CAMERA
        private const val IMAGE_MEAN = 128.0f
        private const val IMAGE_STD = 128.0f
        private const val INPUT_SIZE = 112
        private const val OUTPUT_SIZE = 192

        private fun getCropBitmapByCPU(source: Bitmap?, cropRectF: RectF): Bitmap {
            val resultBitmap = Bitmap.createBitmap(
                cropRectF.width().toInt(),
                cropRectF.height().toInt(), Bitmap.Config.ARGB_8888
            )
            val canvas = Canvas(resultBitmap)

            // draw background
            val paint = Paint(Paint.FILTER_BITMAP_FLAG)
            paint.color = Color.WHITE
            canvas.drawRect( //from  w w  w. ja v  a  2s. c  om
                RectF(0f, 0f, cropRectF.width(), cropRectF.height()),
                paint
            )

            val matrix = Matrix()
            matrix.postTranslate(-cropRectF.left, -cropRectF.top)

            canvas.drawBitmap(source!!, matrix, paint)

            if (source != null && !source.isRecycled) {
                source.recycle()
            }

            return resultBitmap
        }

        private fun rotateBitmap(
            bitmap: Bitmap, rotationDegrees: Int, flipX: Boolean
        ): Bitmap {
            val matrix = Matrix()

            // Rotate the image back to straight.
            matrix.postRotate(rotationDegrees.toFloat())

            // Mirror the image along the X or Y axis.
            matrix.postScale(if (flipX) -1.0f else 1.0f, 1.0f)
            val rotatedBitmap =
                Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)

            // Recycle the old bitmap if it has changed.
            if (rotatedBitmap != bitmap) {
                bitmap.recycle()
            }
            return rotatedBitmap
        }

        private fun YUV_420_888toNV21(image: Image?): ByteArray {
            val width = image!!.width
            val height = image.height
            val ySize = width * height
            val uvSize = width * height / 4

            val nv21 = ByteArray(ySize + uvSize * 2)

            val yBuffer = image.planes[0].buffer // Y
            val uBuffer = image.planes[1].buffer // U
            val vBuffer = image.planes[2].buffer // V

            var rowStride = image.planes[0].rowStride
            assert(image.planes[0].pixelStride == 1)
            var pos = 0

            if (rowStride == width) { // likely
                yBuffer[nv21, 0, ySize]
                pos += ySize
            } else {
                var yBufferPos = -rowStride.toLong() // not an actual position
                while (pos < ySize) {
                    yBufferPos += rowStride.toLong()
                    yBuffer.position(yBufferPos.toInt())
                    yBuffer[nv21, pos, width]
                    pos += width
                }
            }

            rowStride = image.planes[2].rowStride
            val pixelStride = image.planes[2].pixelStride

            assert(rowStride == image.planes[1].rowStride)
            assert(pixelStride == image.planes[1].pixelStride)
            if (pixelStride == 2 && rowStride == width && uBuffer[0] == vBuffer[1]) {
                // maybe V an U planes overlap as per NV21, which means vBuffer[1] is alias of uBuffer[0]
                val savePixel = vBuffer[1]
                try {
                    vBuffer.put(1, savePixel.inv() as Byte)
                    if (uBuffer[0] == savePixel.inv() as Byte) {
                        vBuffer.put(1, savePixel)
                        vBuffer.position(0)
                        uBuffer.position(0)
                        vBuffer[nv21, ySize, 1]
                        uBuffer[nv21, ySize + 1, uBuffer.remaining()]

                        return nv21 // shortcut
                    }
                } catch (ex: ReadOnlyBufferException) {
                    // unfortunately, we cannot check if vBuffer and uBuffer overlap
                }

                // unfortunately, the check failed. We must save U and V pixel by pixel
                vBuffer.put(1, savePixel)
            }

            // other optimizations could check if (pixelStride == 1) or (pixelStride == 2),
            // but performance gain would be less significant
            for (row in 0 until height / 2) {
                for (col in 0 until width / 2) {
                    val vuPos = col * pixelStride + row * rowStride
                    nv21[pos++] = vBuffer[vuPos]
                    nv21[pos++] = uBuffer[vuPos]
                }
            }

            return nv21
        }
    }
}
package nl.thommie.ai;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Size;
import android.view.Surface;
import android.view.TextureView;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

final class CameraVisionController {

    interface Callback {
        void onCameraReady(boolean front);
        void onFrame(byte[] jpeg);
        void onError(String message);
    }

    private final Activity activity;
    private final TextureView preview;
    private final Callback callback;

    private HandlerThread cameraThread;
    private Handler cameraHandler;
    private CameraDevice cameraDevice;
    private CameraCaptureSession captureSession;
    private CaptureRequest.Builder previewRequest;
    private ImageReader imageReader;
    private String cameraId;
    private boolean front;
    private int sensorOrientation = 90;
    private final AtomicBoolean captureInFlight =
            new AtomicBoolean(false);

    CameraVisionController(
            Activity activity,
            TextureView preview,
            Callback callback
    ) {
        this.activity = activity;
        this.preview = preview;
        this.callback = callback;
    }

    void start(boolean useFront) {
        stop();
        front = useFront;
        startThread();

        if (preview.isAvailable()) {
            openSelectedCamera();
        } else {
            preview.setSurfaceTextureListener(
                    new TextureView.SurfaceTextureListener() {
                        @Override
                        public void onSurfaceTextureAvailable(
                                SurfaceTexture surface,
                                int width,
                                int height
                        ) {
                            openSelectedCamera();
                        }

                        @Override
                        public void onSurfaceTextureSizeChanged(
                                SurfaceTexture surface,
                                int width,
                                int height
                        ) {}

                        @Override
                        public boolean onSurfaceTextureDestroyed(
                                SurfaceTexture surface
                        ) {
                            return true;
                        }

                        @Override
                        public void onSurfaceTextureUpdated(
                                SurfaceTexture surface
                        ) {}
                    }
            );
        }
    }

    boolean isFront() {
        return front;
    }

    void switchCamera() {
        start(!front);
    }

    void captureFrame() {
        CameraDevice device = cameraDevice;
        CameraCaptureSession session = captureSession;
        ImageReader reader = imageReader;

        if (device == null
                || session == null
                || reader == null
                || !captureInFlight.compareAndSet(
                        false,
                        true
                )) {
            return;
        }

        try {
            CaptureRequest.Builder still =
                    device.createCaptureRequest(
                            CameraDevice.TEMPLATE_STILL_CAPTURE
                    );

            still.addTarget(
                    reader.getSurface()
            );

            still.set(
                    CaptureRequest.CONTROL_AF_MODE,
                    CaptureRequest
                            .CONTROL_AF_MODE_CONTINUOUS_PICTURE
            );

            still.set(
                    CaptureRequest.JPEG_ORIENTATION,
                    jpegOrientation()
            );

            session.capture(
                    still.build(),
                    new CameraCaptureSession.CaptureCallback() {},
                    cameraHandler
            );

        } catch (Exception e) {
            captureInFlight.set(false);
            reportError(
                    "Cameraframe kon niet worden gemaakt: "
                            + safeMessage(e)
            );
        }
    }

    void stop() {
        captureInFlight.set(false);

        CameraCaptureSession session =
                captureSession;
        captureSession = null;

        if (session != null) {
            try {
                session.stopRepeating();
            } catch (Exception ignored) {}

            try {
                session.close();
            } catch (Exception ignored) {}
        }

        CameraDevice device =
                cameraDevice;
        cameraDevice = null;

        if (device != null) {
            try {
                device.close();
            } catch (Exception ignored) {}
        }

        ImageReader reader =
                imageReader;
        imageReader = null;

        if (reader != null) {
            try {
                reader.close();
            } catch (Exception ignored) {}
        }

        HandlerThread thread =
                cameraThread;
        cameraThread = null;
        cameraHandler = null;

        if (thread != null) {
            try {
                thread.quitSafely();
            } catch (Exception ignored) {}

            try {
                thread.join(500);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void startThread() {
        cameraThread =
                new HandlerThread(
                        "MaatjeCamera"
                );
        cameraThread.start();
        cameraHandler =
                new Handler(
                        cameraThread.getLooper()
                );
    }

    private void openSelectedCamera() {
        if (activity.checkSelfPermission(
                Manifest.permission.CAMERA
        ) != PackageManager.PERMISSION_GRANTED) {
            reportError(
                    "Cameratoegang ontbreekt."
            );
            return;
        }

        CameraManager manager =
                (CameraManager) activity
                        .getSystemService(
                                Context.CAMERA_SERVICE
                        );

        if (manager == null) {
            reportError(
                    "Camera-service is niet beschikbaar."
            );
            return;
        }

        try {
            cameraId =
                    chooseCamera(
                            manager,
                            front
                    );

            if (cameraId == null) {
                reportError(
                        front
                                ? "Geen frontcamera gevonden."
                                : "Geen achtercamera gevonden."
                );
                return;
            }

            CameraCharacteristics characteristics =
                    manager.getCameraCharacteristics(
                            cameraId
                    );

            Integer orientation =
                    characteristics.get(
                            CameraCharacteristics
                                    .SENSOR_ORIENTATION
                    );

            if (orientation != null) {
                sensorOrientation =
                        orientation;
            }

            StreamConfigurationMap map =
                    characteristics.get(
                            CameraCharacteristics
                                    .SCALER_STREAM_CONFIGURATION_MAP
                    );

            if (map == null) {
                reportError(
                        "Camera-outputconfiguratie ontbreekt."
                );
                return;
            }

            Size jpegSize =
                    chooseJpegSize(
                            map.getOutputSizes(
                                    ImageFormat.JPEG
                            )
                    );

            Size previewSize =
                    choosePreviewSize(
                            map.getOutputSizes(
                                    SurfaceTexture.class
                            )
                    );

            imageReader =
                    ImageReader.newInstance(
                            jpegSize.getWidth(),
                            jpegSize.getHeight(),
                            ImageFormat.JPEG,
                            2
                    );

            imageReader.setOnImageAvailableListener(
                    reader ->
                            handleImage(reader),
                    cameraHandler
            );

            SurfaceTexture texture =
                    preview.getSurfaceTexture();

            if (texture == null) {
                reportError(
                        "Camera-preview is nog niet klaar."
                );
                return;
            }

            texture.setDefaultBufferSize(
                    previewSize.getWidth(),
                    previewSize.getHeight()
            );

            manager.openCamera(
                    cameraId,
                    new CameraDevice.StateCallback() {
                        @Override
                        public void onOpened(
                                CameraDevice camera
                        ) {
                            cameraDevice =
                                    camera;
                            createSession();
                        }

                        @Override
                        public void onDisconnected(
                                CameraDevice camera
                        ) {
                            camera.close();
                            cameraDevice = null;
                            reportError(
                                    "Camera is losgekoppeld."
                            );
                        }

                        @Override
                        public void onError(
                                CameraDevice camera,
                                int error
                        ) {
                            camera.close();
                            cameraDevice = null;
                            reportError(
                                    "Camera-fout "
                                            + error
                            );
                        }
                    },
                    cameraHandler
            );

        } catch (Exception e) {
            reportError(
                    "Camera kon niet starten: "
                            + safeMessage(e)
            );
        }
    }

    private void createSession() {
        CameraDevice device =
                cameraDevice;
        ImageReader reader =
                imageReader;

        if (device == null
                || reader == null) {
            return;
        }

        SurfaceTexture texture =
                preview.getSurfaceTexture();

        if (texture == null) {
            return;
        }

        Surface previewSurface =
                new Surface(texture);

        try {
            previewRequest =
                    device.createCaptureRequest(
                            CameraDevice.TEMPLATE_PREVIEW
                    );

            previewRequest.addTarget(
                    previewSurface
            );

            previewRequest.set(
                    CaptureRequest.CONTROL_AF_MODE,
                    CaptureRequest
                            .CONTROL_AF_MODE_CONTINUOUS_PICTURE
            );

            device.createCaptureSession(
                    Arrays.asList(
                            previewSurface,
                            reader.getSurface()
                    ),
                    new CameraCaptureSession.StateCallback() {
                        @Override
                        public void onConfigured(
                                CameraCaptureSession session
                        ) {
                            captureSession =
                                    session;

                            try {
                                session.setRepeatingRequest(
                                        previewRequest.build(),
                                        null,
                                        cameraHandler
                                );

                                activity.runOnUiThread(
                                        () -> callback
                                                .onCameraReady(
                                                        front
                                                )
                                );

                            } catch (Exception e) {
                                reportError(
                                        "Camera-preview kon niet starten: "
                                                + safeMessage(e)
                                );
                            }
                        }

                        @Override
                        public void onConfigureFailed(
                                CameraCaptureSession session
                        ) {
                            reportError(
                                    "Camera-sessie configureren is mislukt."
                            );
                        }
                    },
                    cameraHandler
            );

        } catch (Exception e) {
            reportError(
                    "Camera-sessie kon niet worden gemaakt: "
                            + safeMessage(e)
            );
        }
    }

    private void handleImage(
            ImageReader reader
    ) {
        Image image = null;

        try {
            image =
                    reader.acquireLatestImage();

            if (image == null) {
                return;
            }

            ByteBuffer buffer =
                    image.getPlanes()[0]
                            .getBuffer();

            byte[] bytes =
                    new byte[
                            buffer.remaining()
                    ];

            buffer.get(bytes);

            activity.runOnUiThread(
                    () -> callback
                            .onFrame(bytes)
            );

        } catch (Exception e) {
            reportError(
                    "Camerabeeld kon niet worden gelezen: "
                            + safeMessage(e)
            );
        } finally {
            if (image != null) {
                try {
                    image.close();
                } catch (Exception ignored) {}
            }

            captureInFlight.set(false);
        }
    }

    private String chooseCamera(
            CameraManager manager,
            boolean wantFront
    ) throws CameraAccessException {
        int desired =
                wantFront
                        ? CameraCharacteristics
                                .LENS_FACING_FRONT
                        : CameraCharacteristics
                                .LENS_FACING_BACK;

        String fallback = null;

        for (String id :
                manager.getCameraIdList()) {
            CameraCharacteristics c =
                    manager.getCameraCharacteristics(
                            id
                    );

            Integer facing =
                    c.get(
                            CameraCharacteristics
                                    .LENS_FACING
                    );

            if (fallback == null) {
                fallback = id;
            }

            if (facing != null
                    && facing == desired) {
                return id;
            }
        }

        return fallback;
    }

    private Size chooseJpegSize(
            Size[] sizes
    ) {
        if (sizes == null
                || sizes.length == 0) {
            return new Size(
                    1280,
                    720
            );
        }

        List<Size> list =
                Arrays.asList(sizes);

        Size best =
                Collections.min(
                        list,
                        Comparator.comparingLong(
                                s -> distanceFromTarget(
                                        s,
                                        1280,
                                        720
                                )
                        )
                );

        return best;
    }

    private Size choosePreviewSize(
            Size[] sizes
    ) {
        if (sizes == null
                || sizes.length == 0) {
            return new Size(
                    1280,
                    720
            );
        }

        return Collections.min(
                Arrays.asList(sizes),
                Comparator.comparingLong(
                        s -> distanceFromTarget(
                                s,
                                1280,
                                720
                        )
                )
        );
    }

    private long distanceFromTarget(
            Size size,
            int targetWidth,
            int targetHeight
    ) {
        long dw =
                size.getWidth()
                        - targetWidth;
        long dh =
                size.getHeight()
                        - targetHeight;

        return Math.abs(dw)
                + Math.abs(dh);
    }

    private int jpegOrientation() {
        int rotation =
                activity.getWindowManager()
                        .getDefaultDisplay()
                        .getRotation();

        int deviceDegrees;

        switch (rotation) {
            case Surface.ROTATION_90:
                deviceDegrees = 90;
                break;
            case Surface.ROTATION_180:
                deviceDegrees = 180;
                break;
            case Surface.ROTATION_270:
                deviceDegrees = 270;
                break;
            case Surface.ROTATION_0:
            default:
                deviceDegrees = 0;
                break;
        }

        if (front) {
            return (
                    sensorOrientation
                            + deviceDegrees
            ) % 360;
        }

        return (
                sensorOrientation
                        - deviceDegrees
                        + 360
        ) % 360;
    }

    private void reportError(
            String message
    ) {
        activity.runOnUiThread(
                () -> callback.onError(
                        message
                )
        );
    }

    private String safeMessage(
            Throwable throwable
    ) {
        if (throwable == null
                || throwable.getMessage() == null
                || throwable.getMessage().trim().isEmpty()) {
            return "onbekende fout";
        }

        return throwable.getMessage();
    }
}

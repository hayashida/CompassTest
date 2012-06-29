package jp.kuseful.compasstest;

import java.io.IOException;

import android.app.Activity;
import android.content.Context;
import android.graphics.Point;
import android.hardware.Camera;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.util.Log;
import android.view.Display;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.Window;
import android.view.WindowManager;
import android.widget.TextView;

public class CompassTestActivity extends Activity
	implements SurfaceHolder.Callback, SensorEventListener {
	
	private static final String TAG = "CompassTest";
	
	private static final int MIN_PREVIEW_PIXCELS = 320 * 240;
	private static final int MAX_PREVIEW_PIXCELS = 800 * 480;
	
	private static final int MATRIX_SIZE = 16;
	private static final int DIMENSION = 3;
	
	private Camera myCamera;
	private SurfaceView surfaceView;
	private TextView textView;
	
	private boolean hasSurface;
	private boolean initialized;
	
	private Point screenPoint;
	private Point previewPoint;
	
	private SensorManager sensor;
	
	private float[] magneticValues = new float[DIMENSION];
	private float[] accelerometerValues = new float[DIMENSION];
	
	/** lifecycle */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        Window window = getWindow();
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        
        sensor = (SensorManager)getSystemService(SENSOR_SERVICE);
        
        hasSurface = false;
        initialized = false;
        
        setContentView(R.layout.main);
    }
    
    @Override
    public void onResume() {
    	super.onResume();
    	
    	// カメラプレビュー
    	surfaceView = (SurfaceView)findViewById(R.id.preview_view);
    	SurfaceHolder surfaceHolder = surfaceView.getHolder();
    	if (hasSurface) {
    		initCamera(surfaceHolder);
    	} else {
    		surfaceHolder.addCallback(this);
    		surfaceHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
    	}
    	
    	// センサー
    	sensor.registerListener(this, sensor.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD), SensorManager.SENSOR_DELAY_UI);
    	sensor.registerListener(this, sensor.getDefaultSensor(Sensor.TYPE_ACCELEROMETER), SensorManager.SENSOR_DELAY_UI);
    	
    	// テキスト
    	textView = (TextView)findViewById(R.id.orient_text);
    }
    
    @Override
    public void onPause() {
    	closeCamera();
    	if (!hasSurface) {
    		SurfaceHolder surfaceHolder = surfaceView.getHolder();
    		surfaceHolder.removeCallback(this);
    	}
    	
    	sensor.unregisterListener(this);
    	
    	super.onPause();
    }

    /** SurfaceHolder.Callback */
	@Override
	public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {

	}

	@Override
	public void surfaceCreated(SurfaceHolder holder) {
		if (!hasSurface) {
			hasSurface = true;
			initCamera(holder);
		}
	}

	@Override
	public void surfaceDestroyed(SurfaceHolder holder) {
		hasSurface = false;
	}

	/** SensorEventListener */
	@Override
	public void onAccuracyChanged(Sensor sensor, int accuracy) {

	}

	@Override
	public void onSensorChanged(SensorEvent event) {
		if (event.accuracy == SensorManager.SENSOR_STATUS_UNRELIABLE)
			return;
		
		switch (event.sensor.getType()) {
			case Sensor.TYPE_MAGNETIC_FIELD:	// 地磁気センサ
				magneticValues = event.values.clone();
				break;
			case Sensor.TYPE_ACCELEROMETER:		// 加速度センサ
				accelerometerValues = event.values.clone();
				break;
		}
		
		if (magneticValues != null && accelerometerValues != null) {
			float[] rotationMatrix = new float[MATRIX_SIZE];
			float[] inclinationMatrix = new float[MATRIX_SIZE];
			float[] remapedMatrix = new float[MATRIX_SIZE];
			
			float[] orientationValues = new float[DIMENSION];
			
			// 加速度センサと地磁気センサから回転行列を取得
			SensorManager.getRotationMatrix(rotationMatrix, inclinationMatrix, accelerometerValues, magneticValues);
			SensorManager.remapCoordinateSystem(rotationMatrix, SensorManager.AXIS_X, SensorManager.AXIS_Z, remapedMatrix);
			SensorManager.getOrientation(remapedMatrix, orientationValues);
			
			// 方位を取得する
			int orientDegrees = toOrientationDegrees(orientationValues[0]);
			String orientString = toOrientationString(orientationValues[0]);
			
			textView.setText("orientDegrees = " + orientDegrees + " , orientString = " + orientString);
			Log.d(TAG, "orientDegrees = " + orientDegrees + " , orientString = " + orientString);
		}
	}
	
	/** private method */
	
	/**
	 * カメラ情報を初期化
	 * @param holder
	 */
	private void initCamera(SurfaceHolder holder) {
		try {
			openCamera(holder);
		} catch (Exception e) {
			Log.w(TAG, e);
		}
	}
	
	/**
	 * カメラを起動
	 * @param holder
	 * @throws IOException
	 */
	private void openCamera(SurfaceHolder holder) throws IOException {
		if (myCamera == null) {
			myCamera = Camera.open();
			if (myCamera == null) {
				throw new IOException();
			}
		}
		myCamera.setPreviewDisplay(holder);
		
		if (!initialized) {
			initialized = false;
			initFromCameraParameters(myCamera);
		}
		
		setCameraParameter(myCamera);
		myCamera.setDisplayOrientation(90);
		myCamera.startPreview();
	}
	
	/**
	 * カメラを破棄
	 */
	private void closeCamera() {
		if (myCamera != null) {
			myCamera.stopPreview();
			myCamera.release();
			myCamera = null;
		}
	}
	
	/**
	 * カメラ情報を設定
	 * @param camera
	 */
	private void setCameraParameter(Camera camera) {
		Camera.Parameters parameters = camera.getParameters();
		
		parameters.setPreviewSize(previewPoint.x, previewPoint.y);
		camera.setParameters(parameters);
	}
	
	/**
	 * カメラのプレビューサイズ・画面サイズを設定
	 * @param camera
	 */
	private void initFromCameraParameters(Camera camera) {
		Camera.Parameters parameters = camera.getParameters();
		
		WindowManager manager = (WindowManager)getApplication().getSystemService(Context.WINDOW_SERVICE);
		Display display = manager.getDefaultDisplay();
		int width = display.getWidth();
		int height = display.getHeight();
		
		if (width < height) {
			int tmp = width;
			width = height;
			height = tmp;
		}
		
		screenPoint = new Point(width, height);
		Log.d(TAG, "screenPoint.x = " + screenPoint.x + " , screenPoint.y = " + screenPoint.y);
		previewPoint = findPreviewPoint(parameters, screenPoint, false);
		Log.d(TAG, "previewPoint.x = " + previewPoint.x + " , previewPoint.y = " + previewPoint.y);
	}
	
	/**
	 * 最適なプレビューサイズを取得
	 * @param parameters
	 * @param screenPoint
	 * @param portrait
	 * @return
	 */
	private Point findPreviewPoint(Camera.Parameters parameters, Point screenPoint, boolean portrait) {
		Point previewPoint = null;
		int diff = Integer.MAX_VALUE;
		
		for (Camera.Size supportedPreviewSize : parameters.getSupportedPreviewSizes()) {
			int pixcels = supportedPreviewSize.width * supportedPreviewSize.height;
			if (pixcels < MIN_PREVIEW_PIXCELS || pixcels > MAX_PREVIEW_PIXCELS) {
				continue;
			}
			
			int supportedWidth = portrait ? supportedPreviewSize.height : supportedPreviewSize.width;
			int supportedHeight = portrait ? supportedPreviewSize.width : supportedPreviewSize.height;
			int newDiff = Math.abs(screenPoint.x * supportedHeight - supportedWidth * screenPoint.y);
			
			if (newDiff == 0) {
				previewPoint = new Point(supportedWidth, supportedHeight);
				break;
			}
			
			if (newDiff < diff) {
				previewPoint = new Point(supportedWidth, supportedHeight);
				diff = newDiff;
			}
		}
		
		if (previewPoint == null) {
			Camera.Size defaultPreviewSize = parameters.getPreviewSize();
			previewPoint = new Point(defaultPreviewSize.width, defaultPreviewSize.height);
		}
		
		return previewPoint;
	}
	
	/**
	 * 方位の角度に変換する
	 * @param angrad
	 * @return
	 */
	private int toOrientationDegrees(float angrad) {
		return (int)Math.floor(angrad >= 0 ? Math.toDegrees(angrad) : 360 + Math.toDegrees(angrad));
	}
	
	/**
	 * 方位の文字列に変換する
	 * @param angrad
	 * @return
	 */
	private String toOrientationString(float angrad) {
		double[] orientation_range = {
			- (Math.PI * 3 / 4),	// 南
			- (Math.PI * 1 / 4),	// 西
			+ (Math.PI * 1 / 4),	// 北
			+ (Math.PI * 3 / 4),	// 東
		};
		
		String[] orientation_string = {
			"south",
			"west",
			"north",
			"east",
		};
		
		for (int i = 0; i < orientation_range.length; i++) {
			if (angrad < orientation_range[i]) {
				return orientation_string[i];
			}
		}
		
		return orientation_string[0];
	}
}
package menthallab.waffle;

import java.io.*;
import java.util.*;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.AudioManager;
import android.media.SoundPool;
import android.net.wifi.*;
import android.os.*;
import android.annotation.SuppressLint;
import android.app.*;
import android.bluetooth.BluetoothAdapter;

import android.bluetooth.BluetoothDevice;

import android.content.*;
import android.util.Log;
import android.view.*;
import android.widget.*;

import menthallab.wafflelib.*;

import static menthallab.waffle.R.id.bt_start;
import static menthallab.waffle.R.id.ed_delaytime;


//�����ڲ����ݻ��� ������һ�ε�����?

@SuppressLint("DefaultLocale")
public class GatherActivity extends Activity {

	private EditText ed_times;
	private EditText ed_delaytime;
	private TextView tv_bluetoothNum;
	private Button startButton;
	private Button backButton;
	private EditText editText;	
	private WifiManager wifi;
	private BluetoothAdapter mBluetoothAdapter=null;
	private boolean isWorking;
	private Dataset dataset;
	private int t;
	private SensorManager mSensorManager;
	private Sensor mSensor;
	private Set<String> tmpBlueToothSet;
	private SensorEventListener mSensorListener;

	protected static final int HANDLER_FRESH_TIMES = 1;
	protected static final int HANDLER_FRESH_TIMES_OVER = 2;

	//����һ��soundpool����
	private SoundPool soundPool;


	ArrayList<String> bluetooth_dev_list;
	ArrayList<String> wifi_dev_list;
	ArrayList<String> geomagnetic_dev_list;
	Map<String,String> dataMap;

	final IntentFilter filter = new IntentFilter();

//	����������ɼ�һ������  1000 = 1��
	private  int delaytime;
//	ÿ����Ĳɼ�����
	private  int times;


	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_gather);

		ed_times = (EditText)findViewById(R.id.ed_times);
		ed_delaytime = (EditText)findViewById(R.id.ed_delaytime);
		startButton = (Button)findViewById(bt_start);
		backButton = (Button)findViewById(R.id.bt_back);
		editText = (EditText)findViewById(R.id.edit_roomName);
		tv_bluetoothNum = (TextView)findViewById(R.id.tv_bluetoothNum);
		wifi = (WifiManager) getSystemService(Context.WIFI_SERVICE);
		mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
		bluetooth_dev_list =new ArrayList<String>();
		wifi_dev_list =new ArrayList<String>();
		geomagnetic_dev_list =new ArrayList<String>();
		dataMap = new HashMap<String, String>();
		tmpBlueToothSet = new HashSet<String>();
		dataset = new Dataset();
		filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED);
		filter.addAction(BluetoothDevice.ACTION_FOUND);
		filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);

		dataInit();

		mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
		mSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
		mSensorListener = new MSensorListener();
		mSensorManager.registerListener(mSensorListener, mSensor, SensorManager.SENSOR_DELAY_NORMAL);

		isWorking = false;
		IDGenerator.reset();
		editText.setHint("location " + IDGenerator.getNextId());

        //ʵ������������
		soundPool= new SoundPool(1, AudioManager.STREAM_SYSTEM,5);
		soundPool.load(this,R.raw.sound,1);

	}

	private void dataInit() {
		bluetooth_dev_list.add("abeacon_DC91");
		bluetooth_dev_list.add("abeacon_F4E1");
		bluetooth_dev_list.add("abeacon_F11E");
		bluetooth_dev_list.add("abeacon_EE04");
		bluetooth_dev_list.add("abeacon_E2B6");
		bluetooth_dev_list.add("abeacon_F435");
		bluetooth_dev_list.add("abeacon_DDFD");
		bluetooth_dev_list.add("abeacon_EEB7");

		wifi_dev_list.add("TP-LINK_EE43");
		wifi_dev_list.add("TP-LINK_0772");
		wifi_dev_list.add("TP-LINK_07E1");
		wifi_dev_list.add("TP-LINK_B61C");
		wifi_dev_list.add("TP-LINK_B26B");

		geomagnetic_dev_list.add("Geomagnetic1");
		geomagnetic_dev_list.add("Geomagnetic2");
		geomagnetic_dev_list.add("Geomagnetic3");
	}

	Handler handler = new Handler(){
		@Override
		public void handleMessage(Message msg) {
			super.handleMessage(msg);
			switch (msg.what) {
				case HANDLER_FRESH_TIMES:
					ed_times.setText(times-t + "");
					break;
				case HANDLER_FRESH_TIMES_OVER:
					Toast.makeText(GatherActivity.this,"the data at this point is over! you can go to next point!",Toast.LENGTH_LONG).show();
					startButton.performClick();

					//�ɼ���ɣ���������

					soundPool.play(1,1, 1, 0, 0, 1);
					break;

			}
		}
	};

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.gather, menu);
		return true;
	}
	
	@Override
    public void onResume() {
        super.onResume();
        if (isWorking)
        {
	        registerReceiver(rssiReceiver, new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION));
	    	//ע������
			IntentFilter filter = new IntentFilter();
	        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED);
	        filter.addAction(BluetoothDevice.ACTION_FOUND);
	        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
	        registerReceiver(mReceiver, filter);
	        wifi.startScan();
	        mBluetoothAdapter.startDiscovery();
	        
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (isWorking)
        {
        	unregisterReceiver(rssiReceiver);
        	unregisterReceiver(mReceiver);
        }
    }


    // �������� �ɵ��㹻�˾�������Ъ�� Ҫ��Ȼ�Ͳ��ܼ�������
	BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {        	
        	try{

                // Get the BluetoothDevice object from the Intent
                BluetoothDevice device = intent
                        .getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
				short B_rssi = intent.getExtras().getShort(BluetoothDevice.EXTRA_RSSI);



                String deviceName = device.getName();
				if(bluetooth_dev_list.contains(deviceName)) {
					dataMap.put(deviceName, B_rssi + "");

					//����Ѿ��ɼ���8�� ���ͷ�������Դ
					tmpBlueToothSet.add(deviceName);
					Log.i("size",tmpBlueToothSet.size()+"");
					tv_bluetoothNum.setText(tmpBlueToothSet.size()+"");
					if (tmpBlueToothSet.size() == bluetooth_dev_list.size()) {
						Log.i("cancelDiscovery","cancelDiscovery");
//						unregisterReceiver(mReceiver);
						mBluetoothAdapter.cancelDiscovery();
						return;
					}

				}
				Log.i("name",deviceName);
				mBluetoothAdapter.startDiscovery();

    		}
			catch (Exception e){

			}
	  	}
             
	};
          
  //wifi����  
    BroadcastReceiver rssiReceiver = new BroadcastReceiver() {
    	@Override
        public void onReceive(Context context, Intent intent)
    	{
         try
    		{
    			List<ScanResult> scanResults = wifi.getScanResults();
    		for (ScanResult scanResult : scanResults){
	    		StringBuilder messageBuilder = new StringBuilder();
				String ssid = scanResult.SSID;
				int rssi = scanResult.level;
				if(wifi_dev_list.contains(ssid))
					dataMap.put(ssid,rssi+"");
				//TEXTVIEW
				int signalLevel = WifiLib.calculateSignalLevel(rssi, WifiLib.numberOfLevels + 1);
				String network = String.format("wifi name: %s; Level: %d\n", ssid, signalLevel);
				messageBuilder.append(network);
			}
				wifi.startScan();
    	}
    		catch (Exception e)
    		{

    		}
        }
    };
    
    /** Called when the user clicks the Start button */
    public void startTraining(View view){
    	if (isWorking)
    	{
			ed_times.setEnabled(true);
			ed_delaytime.setEnabled(true);
    		unregisterReceiver(rssiReceiver);
    		unregisterReceiver(mReceiver);		
    		editText.setEnabled(true);
    		backButton.setEnabled(true);
    		startButton.setText("begin");
			ed_times.setText(times + "");
    		isWorking = false;
    		editText.setHint("locate " + IDGenerator.getNextId());
    		try
    		{
    			File sdDir = android.os.Environment.getExternalStorageDirectory();
    			File file1 = new File(sdDir, "/dataset_new.csv");
    			String filePath1 = file1.getAbsolutePath();
    			DatasetManager.saveToFile(dataset, filePath1);

    		}
    		catch (IOException exc)
    		{
    			AlertDialog ad = new AlertDialog.Builder(this).create();
    			ad.setMessage(exc.toString());
    			ad.show();
    		}
    	}else{
    		isWorking = true;

			//�ȶ���ʼ��Ϊ0 Ȼ�����ʱ��������ɨ��

			try {
				delaytime = Integer.parseInt(ed_delaytime.getText().toString());
				times = Integer.parseInt(ed_times.getText().toString());
			}catch (Exception e){
				Toast.makeText(this,"������һ����Ч������",Toast.LENGTH_LONG).show();
				return;
			}

			ed_delaytime.setEnabled(false);
			ed_times.setEnabled(false);

			startButton.setText("stop");
    		editText.setEnabled(false);
    		backButton.setEnabled(false);
    		//ע��wifi
    		registerReceiver(rssiReceiver, new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION));
    		//ע������	

            registerReceiver(mReceiver, filter);
    		wifi.startScan();
    		mBluetoothAdapter.startDiscovery();
			t = 0;
			new Thread(new Runnable() {
				@Override
				public void run() {

					while (isWorking && t < times) {
						t ++;
						try {
							Thread.sleep(delaytime * 1000);
						} catch (InterruptedException e) {
							e.printStackTrace();
						}
						handler.sendEmptyMessage(HANDLER_FRESH_TIMES);
						makeData();
						reInitData();
						reInitBlueTooth();

					}
					handler.sendEmptyMessage(HANDLER_FRESH_TIMES_OVER);

				}
			}).start();
    	}
    }



    /** Called when the user clicks the Back button */
    public void returnBack(View view) {
    	btBackPressed();
    }
    
    /** Called when the user clicks the device Back button */
    @Override
    public void onBackPressed() {
    	btBackPressed();
    }
    
    private void btBackPressed()
    {
        GatherActivity.super.onBackPressed();
    }

	private class MSensorListener implements SensorEventListener {
		@Override
		public void onSensorChanged(SensorEvent event) {

			dataMap.put(geomagnetic_dev_list.get(0),(int)(event.values[0])+"");
			dataMap.put(geomagnetic_dev_list.get(1),(int)event.values[1]+"");
			dataMap.put(geomagnetic_dev_list.get(2),(int)event.values[2]+"");

		}
		@Override
		public void onAccuracyChanged(Sensor sensor, int accuracy) {

		}
	}

	private void reInitData() {
		//�ȶ���ʼ��Ϊ0 Ȼ�����ʱ��������ɨ��
		for(String str:bluetooth_dev_list){
			dataMap.put(str,0+"");
		}
		for(String str:wifi_dev_list){
			dataMap.put(str,0+"");
		}


	}

	private void reInitBlueTooth()
	{
		tmpBlueToothSet.clear();
		mBluetoothAdapter.startDiscovery();
	}

	//��dataMapͬ��Ϊdataset
	private void makeData(){
		Log.w("begain", "������������������������������������");
		Instance instance = new Instance();
		for(Map.Entry<String ,String> entry:dataMap.entrySet()) {
			Log.w(entry.getKey(), entry.getValue());
			instance.add(entry.getKey(), Double.valueOf(entry.getValue()));
		}

		String label = editText.getText().toString();
		if (label.equals(""))
			label = editText.getHint().toString();
		dataset.addInstance(instance,label,true);

		Log.w("end", "������������������������������������");
	}

}



package com.bridgeface.projector;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Timer;
import java.util.TimerTask;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.DisplayMetrics;
import android.view.KeyEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

/**
 * MainActivity. ����������Ӻ�������ݵ���ͼ
 */
public class MainActivity extends Activity {

	private Bundle bundle;
	private InputStream is;
	private DataInputStream dis;
	private FileOutputStream fos;
	private Socket socket;
	private ServerSocket serverSocket = null;
	private NetHandler netHandler;
	private NetThread netThread;
	private Handler countDownHandler;
	private Handler getImageHandler;
	private Handler refreshIpHandler;
	private Handler exceptionHander;
	private Timer countDownTimer; // ����ʱ��ʱ��
	private Timer getImageTimer;
	private TimerTask countDownTask;
	private TimerTask getImageTask;

	// ������ʼ�����沼�ֿؼ�
	private LinearLayout initLayout;
	private TextView screenSize;
	private TextView ipAddress;
	private TextView connectStatus;
	private ProgressBar connect_progress;

	// ������ʾͼƬ����ؼ�
	private LinearLayout showImageLayout;
	private ImageView img;

	// ��������ʱ����ؼ�
	private LinearLayout countDownLayout;
	private TextView leftTime;
	private TextView gameName;

	private boolean thread_flag = true;
	private boolean show_img_flag = true;
	private boolean show_time_flag = false;

	private int get_ip_time = 0;
	private int data_length = 0;
	private int img_num = 0;
	private int img_counter = 0;
	private int interval = 5000;
	private long exit_time = 0;
	private byte[] data_str = null; // ����ַ���
	private byte[] data_bin = null; // ��Ŷ������ļ���ͼƬ��

	/** ��д onCreate() ���� */
	@Override
	protected void onCreate(Bundle savedInstanceState) {

		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		// ��ʼ������
		initVariable();

		if (!netThread.isAlive()) {
			netThread.start();
		}
		refreshLocalIp();
		showScreenSize();
		// ��ʾͼƬ
		startImageLoop(interval);

		exceptionHander = new Handler() {

			@Override
			public void handleMessage(Message msg) {
				Bundle b = msg.getData();
				String data_tran_ecp = b.getString("data_tran_ecp");
				Toast.makeText(getApplicationContext(), data_tran_ecp,
						Toast.LENGTH_LONG).show();
				super.handleMessage(msg);
			}

		};

	}

	@Override
	protected void onDestroy() {
		// �ƺ���
		try {

			thread_flag = false; // ���߳�ֹͣ��Ǽ�Ϊfalse���Ա�ֹͣ�߳�

			if (!serverSocket.isClosed()) {
				serverSocket.close(); // �ر� serverSocket
			}

			// �ر���ʾͼƬ���߳�
			if (getImageTimer != null) {
				getImageTimer.cancel();
			}

			// �ر���ʾ����ʱ���߳�
			if (countDownTimer != null) {
				countDownTimer.cancel();
			}
			
			//ɾ��ͼƬ
			deleteImage();

		} catch (IOException e) {
			e.printStackTrace();
		}
		super.onDestroy();
	}

	/**
	 * �����ؼ�ʱ�����û��Ƿ��˳�����������Ӧ�ƺ���
	 */

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {

		if (keyCode == KeyEvent.KEYCODE_BACK) { // ������ذ�ť

			if ((System.currentTimeMillis() - exit_time) > 2000) { // �����������ʱ����С��
																	// 2 ��

				Toast.makeText(this, "�ٰ�һ���˳�", Toast.LENGTH_SHORT).show();
				exit_time = System.currentTimeMillis();
				System.out.println("���ٵ��һ��");
			} else {
				finish();
			}
		}
		return super.onKeyDown(keyCode, event);
	}

	@Override
	public void onBackPressed() {
		// Do nothing
		// super.onBackPressed(); ���෽����ֱ�ӵ��� finish() ����
	}

	public void initVariable() {

		// ��ʼ����ʼ����ؼ�
		initLayout = (LinearLayout) findViewById(R.id.layout_init);
		screenSize = (TextView) findViewById(R.id.tv_screen_size);
		ipAddress = (TextView) findViewById(R.id.tv_ip_address);
		connectStatus = (TextView) findViewById(R.id.tv_connect_status);

		// ��ʼ����ʾͼƬ����ؼ�
		showImageLayout = (LinearLayout) findViewById(R.id.layout_show_image);
		img = (ImageView) findViewById(R.id.iv_img);

		// ��ʼ������ʱ����ؼ�
		countDownLayout = (LinearLayout) findViewById(R.id.layout_count_down);
		leftTime = (TextView) findViewById(R.id.tv_left_time);
		gameName = (TextView) findViewById(R.id.tv_game_name);

		countDownTimer = new Timer();
		netHandler = new NetHandler();
		bundle = new Bundle();
		netThread = new NetThread();

	}

	/**
	 * ��ʾ��Ļ��С
	 */
	public void showScreenSize() {
		DisplayMetrics metrics = new DisplayMetrics();
		getWindowManager().getDefaultDisplay().getMetrics(metrics);

		int width = metrics.widthPixels;
		int height = metrics.heightPixels;
		System.out.println(metrics.toString());
		screenSize.setText("����ʾ����" + width + "X" + height);
		System.out.println("width-->" + width + "; height-->" + height);
	}

	/**
	 * ��ȡ���� IP
	 */
	public String getLocalIp() {

		get_ip_time++;
		// ��ȡ wifi ����
		WifiManager wifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);

		// �ж� wifi �Ƿ���
		if (!wifiManager.isWifiEnabled()) {
			if (get_ip_time == 1) // �ж��ǵ�һ����ʾ����wifi
				wifiManager.setWifiEnabled(true); // ���� wifi
		}

		int ipAddress = wifiManager.getConnectionInfo().getIpAddress(); // �õ����� int ����

		if (ipAddress > 0) {
			return ("IP��ַ : " + ipToString(ipAddress) + ":7611");
		} else {
			if (get_ip_time < 8) {
				return "IP��ַ�����ڻ�ȡ...";
			} else {
				return "IP��ַ : ������";
			}
		}

		// is_first_get_ip = false;
	}

	/**
	 * �Զ���� IP ������ IP ״̬
	 */
	public void refreshLocalIp() {

		boolean isFirstTime = true;

		// �Զ���� IP ÿ 3����һ��
		refreshIpHandler = new Handler() {

			@Override
			public void handleMessage(Message msg) {
				switch (msg.what) {
				case 0:

					ipAddress.setText(getLocalIp());
					if (getLocalIp().equals("IP��ַ : ������")) {
						connectStatus.setText("���ڼ�����绷��  ");
					} else if (getLocalIp().equals("IP��ַ�����ڻ�ȡ...")) {
						connectStatus.setText("�����������绷��  ");
					} else {
						connectStatus.setText("���ڵȴ���������  ");
					}
					break;
				}
				super.handleMessage(msg);
			}

		};

		new Timer().schedule(new TimerTask() {

			@Override
			public void run() {
				Message msg = new Message();
				msg.what = 0;
				refreshIpHandler.sendMessage(msg);
			}

		}, 5000, 3000);

	}

	/**
	 * �� int ���� ip ת���� String ����
	 */
	public String ipToString(int i) {
		return (i & 0xff) + "." + ((i >> 8) & 0xff) + "." + ((i >> 16) & 0xff)
				+ "." + ((i >> 24) & 0xff);
	}

	public void startImageLoop(int interval) {

		getImageHandler = new Handler() {

			public void handleMessage(Message msg) {
				switch (msg.what) {
				case 1:

					System.out.println("��ʼһ��ѭ��");
					
					// ��ȡ�ⲿ�洢Ŀ¼
					File fl = getExternalCacheDir();
					File imgFile = null;
					String filePath = null;
					String firstFilePath = null;
					
					if (checkSDCard(fl)) {
						firstFilePath = fl.toString() + "/image0.jpg";
						filePath = fl.toString() + "/image" + (img_counter++)
								+ ".jpg";
						imgFile = new File(filePath);
						if (img != null && imgFile.exists()) {
							showImage(filePath);
						} else {
							if (show_time_flag) {
								img_counter = 0;
								//��ʾ����ʱ
								clearScreen();
								countDownLayout.setVisibility(View.FOCUS_FORWARD);
							} else {
								if(new File(firstFilePath).exists()) {
									img_counter = 1;
									showImage(firstFilePath);
								} else {
									//��ʾ��ʼ������
									clearScreen();
									initLayout.setVisibility(View.FOCUS_BACKWARD);
									//show_time_flag = false;
								}
							}
						}

					} else {
						System.out.println("sd����ȡʧ�ܣ�");
					}

					break;

				}
				super.handleMessage(msg);
			}
		};
		getImageTask = new TimerTask() {
			public void run() {
				Message msg = new Message();
				msg.what = 1;
				getImageHandler.sendMessage(msg);
			}
		};
		getImageTimer = new Timer(true);
		getImageTimer.schedule(getImageTask, 0, interval);
	}

	public void stopImageLoop() {
		//
	}

	/** ��ʾָ��·���µ�ͼƬ */
	public void showImage(String file) {

		clearScreen();
		showImageLayout.setVisibility(View.FOCUS_BACKWARD); // ��ͼƬ�ؼ����ڿ���ʾ״̬
		Bitmap bitmap = BitmapFactory.decodeFile(file); // �ӱ���ȡͼƬ
		img.setImageBitmap(bitmap); // ����ͼƬ
		System.out.println("��ʾͼƬ----->" + file);

		// // ��ȡͼƬ·��
		// File fl = MainActivity.this.getExternalCacheDir();
		// checkSDCard(fl);
		// String filePath = fl.toString() + "/image"
		// + (img_counter++) + ".jpg";
		// File file = new File(filePath);
		//
		// // �ж��ļ��Ƿ����
		// if (file.exists()) {
		// clearScreen();
		// showImageLayout.setVisibility(View.FOCUS_BACKWARD); // ��ͼƬ�ؼ����ڿ���ʾ״̬
		//
		// } else {
		//
		// img_counter = 0; //ͼƬ����������
		//
		// //�ӵ�һ�ſ�ʼ��ʾ
		// String firstFile = fl.toString() + "/image"
		// + (img_counter++) + ".jpg";
		// if (new File(firstFile).exists()) {
		// clearScreen();
		// showImageLayout.setVisibility(View.FOCUS_BACKWARD); // ��ͼƬ�ؼ����ڿ���ʾ״̬
		// Bitmap bitmap = BitmapFactory
		// .decodeFile(firstFile); // �ӱ���ȡͼƬ
		// img.setImageBitmap(bitmap); // ����ͼƬ
		// System.out.println("�ӵ�һ�ſ�ʼ��ʾ");
		// System.out.println("��ʾͼƬ----->" + firstFile);

		// loading_progress.setVisibility(View.GONE); // ȥ������ͼƬ����
		// img.setVisibility(View.FOCUS_BACKWARD); // ��ͼƬ�ؼ����ڿ���ʾ״̬
		// //��ʾ����ʱ����
		// showImageLayout.setVisibility(View.GONE); // ��ͼƬ�ؼ����ڲ�����ʾ״̬
		// countDownLayout.setVisibility(View.FOCUS_BACKWARD);
		// }
		// }

	}

	/** ������������пؼ����� �����ؽ������ݣ� */
	public void clearScreen() {

		initLayout.setVisibility(View.GONE);
		showImageLayout.setVisibility(View.GONE);
		countDownLayout.setVisibility(View.GONE);

	}

	/**
	 * ���SD�Ƿ��ȡ��ȷ���������ȷ���򵯳���ȡSD��ʧ�ܵ� Toast
	 * 
	 * @param fl
	 */
	public boolean checkSDCard(File fl) {
		if (fl == null) {
			Toast.makeText(this, "��ȡ�洢��ʧ�ܣ�", Toast.LENGTH_SHORT).show();
			return false;
		} else {
			return true;
		}
	}
	
	
	/** ɾ���Ѿ��洢�ĳ��� temp.jpg ������ͼƬ */
	public void deleteImage() {
		
		show_time_flag = false;

		int i = 0;
		File fl = MainActivity.this.getExternalCacheDir();
		boolean flag = true;
		while (flag) {
			String filePath = fl.toString() + "/image" + (i++) + ".jpg";
			File file = new File(filePath);
			// �ж��ļ��Ƿ����
			if (file.exists()) {
				file.delete();
				System.out.println("ɾ��ͼƬ---->" + filePath);
			} else {
				System.out.println("File to delete not found!");
				img_num = 0;
				flag = false;
			}
		}
	}
	

	/**
	 * �ڲ���.NetThread ���ڽ����������ӵ��̡߳�Android 3.0 �Ժ���������صĲ������� �����߳���
	 */

	class NetThread extends Thread {

		/** �߳���������ø÷�����Thread �������д run ���� */
		@Override
		public void run() {

			// �����˿ڡ��ⲿ�ֲ��ܷ��� connect() ��������һ��ѭ��������ڶ������Ӳ���
			if (serverSocket == null) {
				try {
					serverSocket = new ServerSocket(7611);
				} catch (IOException e) {
					e.printStackTrace();
				}
			}

			// ��һ��ѭ����ѭ���������Է���˷��������ݰ�
			while (thread_flag) {
				if (connect()) {
					receive();
				} else {
					break;
				}
			}

		}

		/** ���ӷ���� */
		public boolean connect() {
			try {

				socket = serverSocket.accept(); // �ȴ���������ӣ��˴�����
				System.out.println("���ӳɹ�");
				is = socket.getInputStream();
				dis = new DataInputStream(is);
				return true;
			} catch (IOException e) {
				System.out.println("����ʧ��");
				e.printStackTrace();
				return false;
			}
		}

		/** ���շ���˷������ݣ�ÿ����һ�ξͽ���һ�Σ� */
		public void receive() {

			// �ڶ���ѭ������������һ�η����˶���ͼƬ��������ݰ�ճ����һ����ѭ������
			// ճ��һ������ݰ�
			while (true) {
				if ((data_length = getDataLen(dis)) > 0) { // �ж� dis�����Ƿ�������
					System.out.println("===========��ʼ��������=============");
					receiveData(); // ��������
					System.out.println("�������ݳɹ�");
					try {
						handleData(); // ��������
						System.out.println("�������ݳɹ�");
						sendData(); // ��������
						System.out.println("�������ݳɹ�");
						System.out.println("============���ǿ��ֵķָ���===========");
						Thread.sleep(50); // ����50ms
					} catch (Exception e) {
						Bundle exceptionBundle = new Bundle();
						exceptionBundle.putString("data_tran_ecp", "���ݴ����쳣��");
						Message msg = new Message();
						msg.setData(exceptionBundle);
						MainActivity.this.exceptionHander.sendMessage(msg);
						e.printStackTrace();
					}
				} else {
					System.out.println("��������");
					break;
				}
			}
		}

		/** �ӷ���˽��մ���ͷ�����ݰ�,������յ��������򷵻� true,���򷵻� false */
		public void receiveData() {

			System.out.println("���ݰ�����Ϊ��" + data_length);

			getData(); // ��ȡ����

			try {
				String str = new String(data_str, "unicode"); // ���ַ���ת���� unicode
																// �ı�
				System.out.println("�ַ������ȣ�" + str.length());
				System.out.println("���յ��ַ�����ϢΪ��");
				System.out.println(str); // ����ַ���
			} catch (UnsupportedEncodingException e) {
				e.printStackTrace();
			}
		}

		/** ��ȡ���ݰ��ĳ��Ȳ����أ�û�������򷵻� -1 */
		public int getDataLen(DataInputStream dis) {

			int data_len = 0; // ���ݰ��ĳ���
			byte[] b = new byte[4]; // ������ݰ����ȵ�������

			try {
				Thread.sleep(0);
			} catch (InterruptedException e1) {
				e1.printStackTrace();
			}

			try {
				int i = dis.read(b);
				if (i != -1) {
					data_len = byteArrayToInt(b, 0);
				} else {
					data_len = -1;
				}
			} catch (IOException e) {
				e.printStackTrace();
			}

			return data_len;
		}

		/** ��ȡ���� */
		public void getData() {

			// �߳�����һ��ʱ��,�ȴ�����ȫ�����ݰ�,�൱�ڻ�������
			try {
				Thread.sleep(0);
			} catch (InterruptedException e1) {
				e1.printStackTrace();
			}

			try {

				// byteFlag����ֱ�ӷ��� if�У���ȻreadByte()�������2��
				byte byteFlag = dis.readByte();

				// ����Ϊ���ı�
				if (byteFlag == 0x01) {
					System.out.println("����Ϊ���ı�");
					data_str = new byte[data_length - 5];
					dis.read(data_str); // ���ַ������� data_str ����
				}

				// ����Ϊ�ı� + ������������
				else if (byteFlag == 0x05) {
					System.out.println("����ΪͼƬ���ı�");

					try {
						File fl = MainActivity.this.getExternalCacheDir();
						checkSDCard(fl); // ���洢��
						String filePath = fl.toString() + "/temp.jpg"; // ��ʱ�洢ͼƬ·��
						System.out.println("������ʱ�ļ���" + filePath);
						fos = new FileOutputStream(filePath); // �ļ������
					} catch (FileNotFoundException e) {
						e.printStackTrace();
					}

					byte[] b2 = new byte[4]; // ��Ŷ������ļ��ĳ���
					dis.read(b2);
					int data_bin_len = byteArrayToInt(b2, 0); // ����������ļ��ĳ���
					int length = data_bin_len;
					System.out.println("ͼƬ�Ĵ�С---->" + length);
					data_bin = new byte[data_bin_len]; // �����ʱ����

					readImageData(dis, data_bin, length);
					writeImageData(fos, data_bin, length);

					// int bufSize = 1024;
					// byte[] buffer = new byte[bufSize];
					//
					// // ��ͼƬ�Ķ��������ݴ����ڴ�
					// // �����ļ���ʱ��Ҫһ��һ��Ĵ棬�ر���ͼƬ����Ȼ���ݻ��𻵡������1024���µĴ�СΪһ����λ
					// while (length > bufSize) {
					// dis.read(buffer);
					// fos.write(buffer);
					// length -= bufSize;
					// }
					// dis.read(data_bin, 0, length); // �Ѷ������ļ����� data_bin ����
					// fos.write(data_bin, 0, length); // �Ѷ������ļ�д���ļ������
					// fos.close(); // �ر��ļ������

					int data_str_len = (data_length - data_bin_len - 9);
					System.out.println("�ַ�����Сdata_str_len:" + data_str_len);
					data_str = new byte[data_str_len];
					dis.read(data_str); // ���ַ������� data_str ����

				} else {
					System.out.println("���ݴ��䷢������"); // �������ʹ��󣬴˴�Ӧ���Ǵ�������
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

		/**
		 * ���������ж�ȡͼƬ����
		 * 
		 * @param dis
		 * @param data_bin
		 * @param length
		 */
		public void readImageData(DataInputStream dis, byte[] data_bin,
				int length) {

			int bufSize = 8;
			int curr_len = 0;
			byte[] buf = new byte[bufSize];

			while (curr_len < (length - bufSize)) {
				try {
					dis.read(data_bin, curr_len, bufSize); // �Ѷ������ļ����� data_bin
															// ����
					curr_len += bufSize;
				} catch (IOException e) {
					e.printStackTrace();
				}
			}

			// ��ȡʣ�ಿ��
			try {
				dis.read(data_bin, curr_len, (length - curr_len));
			} catch (IOException e) {
				e.printStackTrace();
			}

		}

		/**
		 * ��ͼƬ����д���ļ�����
		 * 
		 * @param fos
		 * @param data
		 * @param length
		 */
		public void writeImageData(FileOutputStream fos, byte[] data, int length) {
			try {
				fos.write(data);
				fos.close();
			} catch (IOException e) {
				System.out.println("д������ʧ�ܣ�");
				e.printStackTrace();
			}
		}

		/** ������պ������ */
		public void handleData() throws Exception {

			// ��ȡ��Ϣͷ
			StringBuilder msg = new StringBuilder(); // ���ݰ���Ϣͷ�������Ϣ����
			String str = null;

			try {
				str = new String(data_str, "unicode"); // ���ֽ�����ת���� unicode �ַ���
			} catch (UnsupportedEncodingException e) {
				e.printStackTrace();
			}

			int i = 0;
			// ��ȡ���ݰ�����Ϣͷ msg
			// �˴��� '#' �����û����ַ��������ַ����ܱ� charAt()����ʶ��
			while (str.charAt(i) != '#' && i < 8) {
				msg.append(str.charAt(i));
				i++;
			}
			msg.append(str.charAt(i++));
			msg.append(str.charAt(i++));
			msg.append(str.charAt(i++));

			// ����Ϣͷ�����ж�
			if (msg.toString().equals("ya_M##1")) {

				// ���յ���ϢΪ IDCtip_msg_ImageToProjector ͼƬ���͵�ͶӰ��
				System.out.println("��ϢͷΪ��" + msg.toString());
				String[] msgs = str.split("\n");

				bundle.putString("ya_M", msgs[0]);
				bundle.putString("FlashSpan", msgs[1]);
				bundle.putString("ClearScreen", msgs[2]);
				bundle.putString("FirstPage", msgs[3]);

			} else if (msg.toString().equals("ya_M##2")) {

				// ���յ���ϢΪ IDCtip_msg_ClearProjector ���ͶӰ��Ϣ
				System.out.println("��ϢͷΪ��" + msg.toString());
				bundle.putString("ya_M", "ya_M##2");

			} else if (msg.toString().equals("ya_M##3")) {

				// ���յ���ϢΪ IDCtip_msg_CountDown ����ʱ��Ϣ
				System.out.println("��ϢͷΪ��" + msg.toString());
				String[] msgs = str.split("\n");

				bundle.putString("ya_M", msgs[0]);
				bundle.putString("ya_Event", msgs[1]);
				bundle.putString("endTime_rs", msgs[2]);
				bundle.putString("_MapTime", msgs[3]);
				bundle.putString("FlashSpan", msgs[4]);
				bundle.putString("ClearScreen", msgs[5]);

			} else {

				// ż�������������⣬Ӧ�������ݴ�����󡣴˴�Ӧ�ò�ȡ��Ӧ��ʩ���������������
				System.out.println("���ݴ����쳣");
				throw new Exception("���ݴ����쳣��o(>�n<)o");

			}

		}

		/** ������ͨ�� Message �͵� Handler */
		public void sendData() {

			Message msg = new Message();
			msg.setData(bundle);
			MainActivity.this.netHandler.sendMessage(msg);

		}

		/** �� byte[] ת���� int ���� */
		public int byteArrayToInt(byte[] b, int offset) {
			int value = 0;
			for (int i = 3; i >= 0; --i) {
				value <<= 8;
				value += (int) (b[i] & 0xff);
			}
			return value;
		}

	}

	/**
	 * �ڲ���.��Ϣ������
	 */
	class NetHandler extends Handler {

		/** �޲ι��캯�� */
		public NetHandler() {
		}

		/** ����Ϣ���д����÷���������д */
		@Override
		public void handleMessage(Message msg) {
			super.handleMessage(msg);

			// �˴����Բ��� UI
			Bundle b = msg.getData(); // ��ȡNetThread���͹�������Ϣ
			String ya_M = b.getString("ya_M").trim();

			// ��ϢΪͼƬ���͵�ͶӰ��
			if (ya_M != null && ya_M.equals("ya_M##1")) {

				// ����������Ϣ
				String FlashSpan = b.getString("FlashSpan").trim();
				String ClearScreen = b.getString("ClearScreen").trim();
				String FirstPage = b.getString("FirstPage").trim();

				int interval = 1000 * Integer.parseInt(FlashSpan.substring(11));
				System.out.println("ͼƬ��ʾ���ʱ��----------��" + interval);


				// �ж��Ƿ�������ɾ��ͼƬ��
				if (ClearScreen != null && ClearScreen.equals("ClearScreen##1")) {
					deleteImage(); // ɾ��ͼƬ
				}

				// ����ͼƬ
				saveImage();

			}

			// ��ϢΪ���ͶӰ����ʾ
			if (ya_M != null && ya_M.equals("ya_M##2")) {

				// ���ͶӰ��
				clearScreen();

				// ɾ�����д洢��ͼƬ
				deleteImage();
				
				// ȡ������ʱ�߳�
				if (countDownTimer != null) {
					countDownTimer.cancel();
				}

				// ��ʼ�����棬�����������ؼ���ʾ����
				initLayout.setVisibility(View.FOCUS_BACKWARD);
			}

			// ��ϢΪ��ʾ����ʱ
			if (ya_M != null && ya_M.equals("ya_M##3")) {

				// ����������Ϣ
				String ya_Event = b.getString("ya_Event").trim();
				String endTime_rs = b.getString("endTime_rs").trim();
				String _MapTime = b.getString("_MapTime").trim();
				String flashSpan = b.getString("FlashSpan").trim();
				String ClearScreen = b.getString("ClearScreen").trim();

				//clearScreen();

				// �ж��Ƿ�������ɾ��ͼƬ��
				if (ClearScreen != null && ClearScreen.equals("ClearScreen##1")) {
					deleteImage(); // ɾ��ͼƬ
				}
				
				// ȡ��֮ǰ�ĵ���ʱ�߳�
				if (countDownTimer != null) {
					countDownTimer.cancel();
				}

				// ��ʾ����ʱ����
				//countDownLayout.setVisibility(View.FOCUS_FORWARD);
				show_time_flag = true;

				// ��ʾ��������
				gameName.setText(ya_Event.substring(10));

				// ��ȡ����ʱ�䣬��byte������
				int end_time[] = new int[6];
				for (int i = 12; i < endTime_rs.length(); i++) {
					char c = endTime_rs.charAt(i);
					end_time[i - 12] = (int) c - 48;
					System.out.print(end_time[i - 12] + ",");
				}

				System.out.println();
				// ��ȡ������ʱ�䣬��byte������
				int map_time[] = new int[6];
				for (int i = 10; i < _MapTime.length(); i++) {
					char c = _MapTime.charAt(i);
					map_time[i - 10] = (int) c - 48;
					System.out.print(map_time[i - 10] + ",");
				}
				System.out.println();

				int year = end_time[5] - map_time[5];
				int mon = end_time[4] - map_time[4];
				int day = end_time[3] - map_time[3];
				int hour = end_time[2] - map_time[2];
				int min = end_time[1] - map_time[1];
				int sec = end_time[0] - map_time[0];

				int sec_total = (sec + min * 60 + hour * 3600 + day * 86400);
				leftTime.setVisibility(View.FOCUS_BACKWARD);
				leftTime.setText(sec_total + "");

				countDownHandler = new Handler() {
					String str = leftTime.getText().toString(); // �� TextView
					// ��ȡ�ܹ�ʣ��ʱ��
					int sec_total = Integer.parseInt(str); // ���ַ���ת���� int

					public void handleMessage(Message msg) {
						sec_total--;
						switch (msg.what) {
						case 1:
							leftTime.setText(formatTime(sec_total));
							break;
						}
						super.handleMessage(msg);
					}
				};
				countDownTask = new TimerTask() {
					public void run() {
						Message msg = new Message();
						msg.what = 1;
						countDownHandler.sendMessage(msg);
					}
				};
				countDownTimer = new Timer(true);
				countDownTimer.schedule(countDownTask, 0, 1000);
			}
		}

		/** ����ͼƬ */
		public void saveImage() {

			String tempFilePath = MainActivity.this.getExternalCacheDir()
					.toString() + "/temp.jpg"; // ��ȡ��ʱ�洢ͼƬ·��
			String newFilePath = MainActivity.this.getExternalCacheDir()
					.toString() + "/image" + (img_num++) + ".jpg"; // ���¶���ͼƬ·��
			File tempFile = new File(tempFilePath);
			File newFile = new File(newFilePath);
			tempFile.renameTo(newFile); // ����ʱͼƬ����������
			System.out.println("����ͼƬ----->" + newFilePath);
		}


		/** ��ʱ�䣨�룩ת���� MM:SS ��ʽ */
		public String formatTime(int seconds) {

			String time = null;
			String mm, ss;
			int m = seconds / 60;
			int s = (seconds - m * 60);

			if (Math.abs(m) < 10) {
				mm = "0" + Math.abs(m);
			} else {
				mm = "" + Math.abs(m);
			}

			if (Math.abs(s) < 10) {
				ss = "0" + Math.abs(s);
			} else {
				ss = "" + Math.abs(s);
			}

			if (seconds >= 0) { // ��ʱ��Ϊ����
				time = mm + ":" + ss;
			} else { // ��ʱ��Ϊ����
				time = "-" + mm + ":" + ss;
			}
			return time;
		}
	}

}

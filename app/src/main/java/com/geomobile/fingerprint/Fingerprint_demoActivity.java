package com.geomobile.fingerprint;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.AudioManager;
import android.media.SoundPool;
import android.os.Bundle;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.ToggleButton;

import com.android.fplibs.DeviceControl;
import com.android.fplibs.fingerprint_native;
import com.serialport.SerialPort;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

public class Fingerprint_demoActivity extends Activity implements OnClickListener {
    /**
     * Called when the activity is first created.
     */

    private Button startReg;
    private Button startQuery;
    private Button upImage;
    private Button looptest;
    private TextView mainStatus;
    private fingerprint_native port;
    private DeviceControl fpdev;
    private ReadThread rt;
    private SharedPreferences.Editor NameList;
    private SharedPreferences ListData;
    private PowerManager pM = null;
    private WakeLock wK = null;

    private static final String TAG = "FingerPrintDemo";
    SerialPort serialPort;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        startReg = (Button) findViewById(R.id.button_sreg);
        startReg.setOnClickListener(this);

        startQuery = (Button) findViewById(R.id.button_squery);
        startQuery.setOnClickListener(this);

        upImage = (Button) findViewById(R.id.button_image);
        upImage.setOnClickListener(this);

        looptest = (Button) findViewById(R.id.btloop);
        looptest.setOnClickListener(this);

        mainStatus = (TextView) findViewById(R.id.textView_status);
        port = new fingerprint_native();

        NameList = getSharedPreferences("namelist", 0).edit();
        ListData = getSharedPreferences("namelist", 0);
        port.OpenSerialPort();

        fpdev = new DeviceControl();
//		if(fpdev.OpenDevice("/proc/driver/scan") < 0)
        if (fpdev.OpenDevice("/sys/class/misc/mtgpio/pin") < 0) {
            mainStatus.setText("open dev control file failed");
            port.CloseSerialPort();
            fpdev = null;
            return;
        }

        pM = (PowerManager) getSystemService(POWER_SERVICE);
        if (pM != null) {
            wK = pM.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK, "15693_lock");
        }
    }

    public void onDestroy() {

        super.onDestroy();
        fpdev.CloseDevice();
        port.CloseSerialPort();
        // TODO Auto-generated method stub
    }

    public void onPause() {

        super.onPause();
        rt.interrupt();
        fpdev.PowerOffDevice();
        // TODO Auto-generated method stub
        wK.release();
    }

    public void onResume() {

        super.onResume();
        if (fpdev.PowerOnDevice() != 0) {
            mainStatus.setText("power on device failed");
            fpdev.CloseDevice();
            port.CloseSerialPort();
        }

        wK.acquire();
        rt = new ReadThread();
        rt.start();
        // TODO Auto-generated method stub
    }

    @Override
    public void onClick(View arg0) {
        // TODO Auto-generated method stub
        if (arg0 == startReg) {
            RegisterFPDialog RFD = new RegisterFPDialog(this);
            RFD.setTitle("Register a Fingerprint");
            RFD.show();
        } else if (arg0 == startQuery) {
            QueryFPDialog QFD = new QueryFPDialog(this);
            QFD.setTitle("Scan a Fingerprint");
            QFD.show();
        } else if (arg0 == looptest) {
            LoopFPDialog LD = new LoopFPDialog(this);
            LD.setTitle("loop test finger");
            LD.show();
        } else if (arg0 == upImage) {
            if (port.DetectFinger() != 0) {
                mainStatus.setText("Can't detect finger");
                return;
            }
            if (port.GetImage() == null) {
                mainStatus.setText("Get image failed, please retry");
                return;
            }
            byte[] img = port.UpImage();
            if (img == null) {
                mainStatus.setText("Upload Image failed, please retry");
                return;
            }

            byte[] res = port.ChangeToBMP(img);
            //now res is the raw data of a BMP image, you can save it directly to a file just like below or do other thing.

            try {            //a.bmp is store in /data/data/com.geomobile.fingerprint/files/a.bmp , can use adb to get it.
                FileOutputStream out = this.openFileOutput("a.bmp", Context.MODE_WORLD_READABLE);
                out.write(res);
                out.close();
            } catch (FileNotFoundException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
            Log.d(TAG, "file saved");

            //below is use to display bmp image on screen
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inPreferredConfig = Bitmap.Config.ALPHA_8;
            Bitmap mp = BitmapFactory.decodeByteArray(res, 0, res.length, options);
            if (mp == null) {
                Log.e(TAG, "can't decode 4bit bmp");
                return;
            }
            ImageView pic = (ImageView) findViewById(R.id.imageView1);
            pic.setImageBitmap(mp);

            mainStatus.setText("Display image ok");
            return;
        }
    }

    private class ReadThread extends Thread {
        @Override
        public void run() {
            super.run();
            while (!isInterrupted()) {
                port.fillbuf();
            }
        }
    }

    class RegisterFPDialog extends Dialog implements android.view.View.OnClickListener {

        private Button regScan;
        private Button regCancle;
        private TextView regStatus;
        private EditText regBox;
        private int process = 0;

        public RegisterFPDialog(Context context) {
            super(context);
            // TODO Auto-generated constructor stub
        }

        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            setContentView(R.layout.register);

            regScan = (Button) findViewById(R.id.button_regscan);
            regScan.setOnClickListener(this);
            regCancle = (Button) findViewById(R.id.button_regexit);
            regCancle.setOnClickListener(this);

            regStatus = (TextView) findViewById(R.id.textView_regstatus);
            regStatus.setText("Place your finger on sensor then press key SCAN\n");
            regBox = (EditText) findViewById(R.id.editText_name);
            regBox.setEnabled(false);
        }

        @Override
        public void onClick(View v) {
            // TODO Auto-generated method stub
            if (v == regScan) {
                if (process == 0) {
                    int res = port.DetectFinger();
                    if (res == 0) {
//						regStatus.setText("Dectect a finger\n");
                    } else if (res == 2) {
                        regStatus.setText("No finger detected, please retry\n");
                        return;
                    } else {
                        regStatus.setText("Error, please retry\n");
                        return;
                    }

                    int[] ray = port.GetImage();
                    if (ray == null) {
                        regStatus.setText("Get finger image failed, please retry\n");
                        return;
                    }

                    res = port.GetTemplet(fingerprint_native.CHAR_BUFFER_A);
                    if (res != 0) {
                        regStatus.setText("Generate templet failed\n");
                        return;
                    }
                    regStatus.setText("Please Scan again\n");
                    process = 1;

                } else if (process == 1) {
                    int res = port.DetectFinger();
                    if (res == 0) {
//						regStatus.setText("Dectect a finger\n");
                    } else if (res == 2) {
                        regStatus.setText("No finger detected, please retry\n");
                        return;
                    } else {
                        regStatus.setText("Error, please retry\n");
                        return;
                    }

                    int[] ray = port.GetImage();
                    if (ray == null) {
                        regStatus.setText("Get finger image failed, please retry\n");
                        return;
                    }

                    res = port.GetTemplet(fingerprint_native.CHAR_BUFFER_B);
                    if (res != 0) {
                        regStatus.setText("Generate templet failed\n");
                        return;
                    }
                    regStatus.setText("Please input a name\n");
                    regScan.setText("Register");
                    regBox.setEnabled(true);
                    process = 2;

                } else if (process == 2) {
                    String uName = regBox.getText().toString();
                    if ((uName == null) || (uName.equals(""))) {
                        regStatus.setText("Please input a name\n");
                        return;
                    }

                    int total = ListData.getInt("total", -1);
                    Log.w(TAG, "total is " + total);
                    if (total == -1) {
                        Log.w(TAG, "First use init\n");
//						NameList.putInt("total", 1);
//						NameList.commit();
                        total = 1;
                        if (port.EraseAllTemplet() != 0) {
                            regStatus.setText("Clear templet library failed\n");
                        }
                    }
                    if (total >= fingerprint_native.MAX_TEMPLET_STORE) {
                        regStatus.setText("Templet library is full, can't register\n");
                        return;
                    }

                    int res = port.MergeTwoTemplet();
                    if (res != 0) {
                        regStatus.setText("Merge finger failed, maybe twice scan are not same finger\n");
                        return;
                    }

                    int place = total;

                    res = port.StoreTemplet(fingerprint_native.MODEL_BUFFER, place);
                    if (res != 0) {
                        regStatus.setText("Store templet failed\n");
                        return;
                    }
                    NameList.putString("list" + Integer.toString(place), uName);
                    NameList.commit();
                    total++;
//					Log.w(TAG, "total is " + total);
                    NameList.putInt("total", total);
                    NameList.commit();

                    regStatus.setText("Register OK, you can register another finger or return to main window\n");
                    regScan.setText("SCAN");
                    regBox.setText("");
                    regBox.setEnabled(false);
                    process = 0;
                }
            } else if (v == regCancle) {
                dismiss();
            }

        }

    }

    class LoopFPDialog extends Dialog implements android.view.View.OnClickListener, OnCheckedChangeListener {

        private ToggleButton bt;
        private Button cl;
        private TextView st;
        //		private Handler hd;
        private Boolean isloop;
        private LoopThread lp = null;
        private SoundPool soundPool;
        private int soundId;

        public LoopFPDialog(Context context) {
            super(context);
            // TODO Auto-generated constructor stub
        }

        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            setContentView(R.layout.loop);

            bt = (ToggleButton) findViewById(R.id.toggleButton_loop);
            bt.setOnCheckedChangeListener(this);
            cl = (Button) findViewById(R.id.button_loop);
            cl.setOnClickListener(this);

            st = (TextView) findViewById(R.id.textView_loopstatus);

            soundPool = new SoundPool(2, AudioManager.STREAM_MUSIC, 0);
            if (soundPool == null) {
                Log.e("15693", "Open sound failed");
            }
            soundId = soundPool.load("/system/media/audio/ui/VideoRecord.ogg", 0);

/*			hd = new Handler() {
                public void handleMessage(Message msg)
				{
					super.handleMessage(msg);
					if(msg.obj != null)
					{
						String m = (String)msg.obj;
						st.append(m);
						st.append("\n\n");
					}
				}
			};*/
        }

        @Override
        protected void onStop() {
            // TODO Auto-generated method stub
            Log.w("stop", "im stopping");
            if (lp != null) {
                isloop = false;
                lp.interrupt();
                lp = null;
            }
            soundPool.release();
            super.onStop();
        }

        @Override
        public void onCheckedChanged(CompoundButton arg0, boolean arg1) {
            // TODO Auto-generated method stub
/*			if(arg1)
            {
					int res = port.DetectFinger();
					if(res != 0)
					{
						Log.e("fps", "lllll can't detect finger");
						return;
					}
				
					int[] ray = port.GetImage();
					if(ray == null)
					{
						Log.e("fps", "lllll no image get");
						return;
					}
				
					res = port.GetTemplet(fingerprint_native.CHAR_BUFFER_A);
					if(res != 0)
					{
						Log.e("fps", "lllll get templet failed");
						return;
					}
					
					byte[] ks = port.UpTemplet(fingerprint_native.CHAR_BUFFER_A);
					if(ks == null)
					{
						Log.e("fps", "lllll up templet A failed");
						return;
					}
					if(ks.length < 256)
					{
						Log.e("fps", "lllll templet A length is " + ks.length);
						return;
					}
					
					res = port.DownTemplet(fingerprint_native.CHAR_BUFFER_B, ks);
					if(res != 0)
					{
						Log.e("fps", "lllll down templet failed");
						return;
					}
					
					try {
						Thread.sleep(100);
					} catch (InterruptedException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
					
					ray = port.MatchTwoTemplet();
					if(ray == null)
					{
						Log.e("fps", "lllll match failed");
						return;
					}
					else
					{
						Log.i("fps", "lllll match ok");
						return;
					}
					
			}*/
            if (arg1) {
                lp = new LoopThread();
                isloop = true;
                lp.start();
            } else {
                isloop = false;
                lp.interrupt();
                lp = null;
            }
        }

        class LoopThread extends Thread {
            @Override
            public void run() {
                super.run();
                while (isloop) {
                    int res = port.DetectFinger();
                    if (res == 0) {
                        int[] ray = port.GetImage();
                        if (ray != null) {
                            soundPool.play(soundId, 1, 1, 0, 0, 1);
                        }
                    }

                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        // TODO Auto-generated catch block
                    }
                }
            }
        }

        @Override
        public void onClick(View arg0) {
            // TODO Auto-generated method stub
            if (lp != null) {
                isloop = false;
                lp.interrupt();
                lp = null;
            }
            soundPool.release();
            dismiss();
        }
    }

    class QueryFPDialog extends Dialog implements android.view.View.OnClickListener {

        private Button qryScan;
        private Button qryCancle;
        private Button qryDelete;
        private TextView qryStatus;
        private int CurrentPlace = 10000;

        public QueryFPDialog(Context context) {
            super(context);
            // TODO Auto-generated constructor stub
        }

        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            setContentView(R.layout.query);

            qryScan = (Button) findViewById(R.id.button_qryscan);
            qryScan.setOnClickListener(this);
            qryCancle = (Button) findViewById(R.id.button_qryexit);
            qryCancle.setOnClickListener(this);
            qryDelete = (Button) findViewById(R.id.button_qrydel);
            qryDelete.setOnClickListener(this);
            qryDelete.setEnabled(false);

            qryStatus = (TextView) findViewById(R.id.textView_loopstatus);
        }

        @Override
        public void onClick(View v) {
            // TODO Auto-generated method stub
            if (v == qryScan) {
                qryDelete.setEnabled(false);
                int res = port.DetectFinger();
                if (res == 0) {
//					qryStatus.setText("Dectect a finger\n");
                } else if (res == 2) {
                    qryStatus.setText("No finger detected, please retry\n");
                    return;
                } else {
                    qryStatus.setText("Error, please retry\n");
                    return;
                }

                int[] ray = port.GetImage();
                if (ray == null) {
                    qryStatus.setText("Get finger image failed\n");
                    return;
                }

                res = port.GetTemplet(fingerprint_native.CHAR_BUFFER_A);
                if (res != 0) {
                    qryStatus.setText("Generate templet failed\n");
                    return;
                }

                int total = ListData.getInt("total", -1);
                if (total == -1) {
                    qryStatus.setText("No finger has registered");
                    return;
                }

                ray = port.SearchTemplet(fingerprint_native.CHAR_BUFFER_A, 0, (total >= fingerprint_native.MAX_TEMPLET_STORE ? fingerprint_native.MAX_TEMPLET_STORE - 1 : total));
                if ((ray == null) || (ray[0] < 0) || (ray[0] >= fingerprint_native.MAX_TEMPLET_STORE)) {
                    qryStatus.setText("Can't find the finger, may not registered, can retry\n");
                    return;
                }
                CurrentPlace = ray[0];
                String uName = ListData.getString("list" + Integer.toString(ray[0]), null);
                if (uName == null) {
                    qryStatus.setText("find finger templet, but name is not registered\n");
                    return;
                }
                qryStatus.setText("Searched, you are " + uName + "\n");
                qryDelete.setEnabled(true);

            } else if (v == qryDelete) {
                if (CurrentPlace == 10000) {
                    qryDelete.setEnabled(false);
                    return;
                }
                if (port.DeletOneTemplet(CurrentPlace) != 0) {
                    qryStatus.setText("Delete templet failed\n");
                    return;
                }
                NameList.remove("list" + CurrentPlace);
                NameList.commit();
                CurrentPlace = 10000;
                qryStatus.setText("");
                qryDelete.setEnabled(false);
            } else if (v == qryCancle) {
                dismiss();
            }

        }

    }
}
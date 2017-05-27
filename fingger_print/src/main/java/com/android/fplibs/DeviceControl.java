package com.android.fplibs;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

public class DeviceControl
{
	private BufferedWriter CtrlFile = null;
	private static final String TAG = "CtrlDEV";
	
	public int OpenDevice(String path)// throws IOException	
	{
		if(CtrlFile != null)
		{
			return 0;									//avoid repeat open
		}
		File DeviceName = new File(path);
		try {
			CtrlFile = new BufferedWriter(new FileWriter(DeviceName, false));
		} catch (IOException e) {
			// TODO Auto-generated catch block
//			e.printStackTrace();
			return -1;
		}	//open file
		return 0;
	}
	
	public int PowerOnDevice()// throws IOException		//poweron barcode device
	{
		if(CtrlFile == null)
		{
			return -1;
		}
		try {
			CtrlFile.write("-wdout94 1");
			CtrlFile.flush();
		} catch (IOException e) {
			// TODO Auto-generated catch block
//			e.printStackTrace();
			return -1;
		}
		return 0;
	}
	
	public int PowerOffDevice()// throws IOException	//poweroff barcode device
	{
		if(CtrlFile == null)
		{
			return -1;
		}
  		try {
			CtrlFile.write("-wdout94 0");
			CtrlFile.flush();
		} catch (IOException e) {
			// TODO Auto-generated catch block
//			e.printStackTrace();
			return -1;
		}
  		return 0;
	}
	
/*	public void TriggerOnDevice() throws IOException	//make barcode begin to scan
	{
		CtrlFile.write("trig");
		CtrlFile.flush();
	}
	
	public void TriggerOffDevice() throws IOException	//make barcode stop scan
	{
		CtrlFile.write("trigoff");
		CtrlFile.flush();
	}
*/	
	public int CloseDevice()// throws IOException		//close file
	{
		if(CtrlFile == null)
		{
			return -1;
		}
		try {
			CtrlFile.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
//			e.printStackTrace();
			return -1;
		}
		return 0;
	}
}
package org.zz.jni; 
public class zzFingerAlg{
	
	public native int mxGetVersion(byte[] version);
	
	public native int mxFingerMatchBase64(byte[] mbBuf,byte[] tzBuf,int level);  

	public native int mxFingerMatch256(byte[] mbBuf,byte[] tzBuf,int level);  

	static {
		System.loadLibrary("FingerAlg");
	}
	
}

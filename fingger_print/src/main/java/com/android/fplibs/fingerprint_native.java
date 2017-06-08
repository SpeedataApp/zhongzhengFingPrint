package com.android.fplibs;

import android.serialport.SerialPort;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static android.serialport.SerialPort.SERIAL_TTYMT1;


public class fingerprint_native {
    private int fd = -1;
    private int delay = 100;
    private RingByteBuffer iobuf;
    private int clearbuf = 0;
    private static final int retry_max = 20;

    private static final String TAG = "fingerprint_demo_native";
    private static final int IO_BLOCK_SIZE = 256;
    private static final byte CMD_HEAD = 1;
    private static final byte DATA_HEAD = 2;
    private static final byte RES_HEAD = 7;
    private static final byte END_HEAD = 8;
    private static final int DEFAULT_DELAY = 100;
    private static final int MAX_FRAME_SIZE = 137;
    private static final int TEMPLET_LENGTH = 256;

    public static final int MAX_TEMPLET_STORE = 1776;
    public static final byte CHAR_BUFFER_A = 1;
    public static final byte CHAR_BUFFER_B = 2;
    public static final byte MODEL_BUFFER = 3;
    public static final byte SECURE_LEVEL_1 = 1;
    public static final byte SECURE_LEVEL_2 = 2;
    public static final byte SECURE_LEVEL_3 = 3;
    public static final byte SECURE_LEVEL_4 = 4;
    public static final byte SECURE_LEVEL_5 = 5;
    public static final byte LIGHT_ON = 1;
    public static final byte LIGHT_OFF = 0;
    public static final byte LIVEBODY_VERIFY_LEVE_OFF = 0;
    public static final byte LIVEBODY_VERIFY_LEVE_LOW = 1;
    public static final byte LIVEBODY_VERIFY_LEVE_HIGH = 2;

    private static final int img_width = 256;
    private static final int img_height = 304;
    //	private static final byte[] img_head = {0x42, 0x4D, 0x36, (byte)0x90, 3, 0, 0, 0,  0, 0, 0x36, 0, 0, 0, 0x28, 0,  0, 0, 0, 1, 0, 0, 0x30, 1,  0, 0, 1, 0, 0x18, 0, 0, 0,  0, 0, 0, (byte)0x90, 3, 0, 0, 0,  0, 0, 0, 0, 0, 0, 0, 0,  0, 0, 0, 0, 0, 0};
    private static final byte[] bmp_head = {0x42, 0x4D, 0x76, (byte) 0x98, 0, 0, 0, 0, 0, 0, 0x76, 0, 0, 0, 0x28, 0, 0, 0, 0, 1, 0, 0, 0x30, 1, 0, 0, 1, 0, 4, 0, 0, 0, 0, 0, 0, (byte) 0x98, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};
    private static final byte[] bmp_palette = new byte[64];

    public fingerprint_native() {
        iobuf = new RingByteBuffer(img_width * img_height);
        for (int i = 0; i < 16; i++) {
            bmp_palette[i * 4] = (byte) (i * 16);
            bmp_palette[i * 4 + 1] = (byte) (i * 16);
            bmp_palette[i * 4 + 2] = (byte) (i * 16);
            bmp_palette[i * 4 + 3] = 0;
        }
    }

    SerialPort serialPort;

    public void OpenSerialPort() {
        serialPort = new SerialPort();
        try {
            serialPort.OpenSerial(SERIAL_TTYMT1, 57600);
            fd=serialPort.getFd();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void CloseSerialPort() {
        Log.d(TAG, "close dev ok");
        closeport(fd);
        fd = -1;
    }

    private int bti(byte a) {
        return (a < 0 ? a + 256 : a);
    }

    private boolean check_crc(byte[] pkg) {
        int crc = 0, crc_c = 0;
        int plength = pkg.length;
//		Log.d(TAG, "in crc pkg length is " + plength);
        for (int i = 0; i < plength - 2; i++) {
            crc += bti(pkg[i]);
        }
        crc_c = (bti(pkg[plength - 2]) << 8) + bti(pkg[plength - 1]);
        return (crc & 0xffff) == (crc_c & 0xffff);
    }

    private byte[] encodepkg(byte[] cmd) {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        buf.write(0xC0);
        for (byte n : cmd) {
            if (bti(n) == 0xC0) {
                buf.write(0xDB);
                buf.write(0xDC);
            } else if (bti(n) == 0xDB) {
                buf.write(0xDB);
                buf.write(0xDD);
            } else {
                buf.write(n);
            }
        }
        buf.write(0xC0);
        return buf.toByteArray();
    }

    private byte[] decodepkg(byte[] cmd) {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        for (int i = 0; i < cmd.length; ) {
            if ((bti(cmd[i]) == 0xDB) && (bti(cmd[i + 1]) == 0xDC)) {
//				Log.d(TAG, "merge 0xDB & 0xDC to 0xC0");
                buf.write(0xC0);
                i += 2;
            } else if ((bti(cmd[i]) == 0xDB) && (bti(cmd[i + 1]) == 0xDD)) {
//				Log.d(TAG, "merge 0xDB & 0xDD to 0xDB");
                buf.write(0xDB);
                i += 2;
            } else {
                buf.write(cmd[i++]);
            }

        }
        return buf.toByteArray();
    }

    private byte[] packet(byte type, byte[] cmd) {
        while (clearbuf > 0) {
            try {
                Thread.sleep(300);
            } catch (InterruptedException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
//			Log.w(TAG, "wait for clear buf");
        }
        if ((type != CMD_HEAD) && (type != DATA_HEAD) && (type != END_HEAD)) {
            return null;
        }
        int clength = cmd.length;
        if (clength > 128) {
            return null;
        }

        byte[] buf = new byte[clength + 9];
        buf[0] = type;
        buf[1] = 0;
        buf[2] = 0;
        buf[3] = 0;
        buf[4] = 0;
        buf[5] = (byte) ((clength >> 8) & 0xff);
        buf[6] = (byte) (clength & 0xff);
        System.arraycopy(cmd, 0, buf, 7, clength);
        int crc = 0;
        for (int i = 0; i < buf.length - 2; i++) {
            crc += bti(buf[i]);
        }
        buf[clength + 7] = (byte) ((crc >> 8) & 0xff);
        buf[clength + 8] = (byte) (crc & 0xff);

        return encodepkg(buf);
    }

    private byte[] unpack(byte[] pkg) {
        int plength = pkg.length;
//		Log.d(TAG, "in upack pkg length is " + plength);
        if (((pkg[0] != RES_HEAD) && (pkg[0] != DATA_HEAD) && (pkg[0] != END_HEAD)) || (plength < 10)) {
            Log.e(TAG, "pkg error");
            return null;
        }
        int packl = (bti(pkg[5]) << 8) + bti(pkg[6]);
        if (packl != (plength - 9)) {
            Log.e(TAG, "pkg length don't match");
            return null;
        }
        byte[] cmd = new byte[plength - 9];
        System.arraycopy(pkg, 7, cmd, 0, cmd.length);
//		Log.d(TAG, "unpack finish cmd length is " + cmd.length);
        return cmd;
    }

    private byte[] getframe() {
        int count = 0, time = 0;
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        int tmp;
        do {
//			synchronized (iobuf) {
            tmp = iobuf.read();
//			}
            if (tmp == 1000) {
                try {
                    Thread.sleep(2);
                } catch (InterruptedException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
                time += 2;
                if (time > 1000) {
                    Log.e(TAG, "time out while waiting a frame header");
                    return null;
                }
            } else {
//				Log.v(TAG, Byte.toString((byte)tmp));
            }
        }
        while ((byte) tmp != (byte) 0xC0);
//		Log.d(TAG, "got a frame header");
//		Log.d(TAG, "use " + time + " ms");
//		Log.d(TAG, "iobuf has " + iobuf.get_size() + " bytes");
        while (true) {
//			synchronized (iobuf) {
            tmp = iobuf.read();
//			}
            if ((byte) tmp == (byte) 0xC0) {
                if (buf.size() == 0) {
                    Log.e(TAG, "0xc0 by 0xc0 is wrong");
                    return null;
                }
//				Log.d(TAG, "get a frame end length is " + count);
//				Log.d(TAG, "use " + time + " ms");
                break;
            } else if (tmp == 1000) {
                try {
                    Thread.sleep(2);
                } catch (InterruptedException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
                time += 2;
                if (time > 1000) {
                    Log.e(TAG, "time out while waiting a frame end");
                    Log.e(TAG, "only get " + count + " bytes");
                    return null;
                }
                continue;
            } else {
                if (count == MAX_FRAME_SIZE) {
                    Log.w(TAG, "frame is larger");
                }
                buf.write(tmp);
                count++;
            }
        }
        return buf.toByteArray();
    }

    private byte[] handledatapkg() {
        byte[] pkg;
        ByteArrayOutputStream res = new ByteArrayOutputStream();
        do {
            pkg = getframe();
            if (pkg == null) {
                Log.e(TAG, "don't get frame");
                clearbuf = retry_max;
                return null;
            }
            pkg = decodepkg(pkg);
            if (!check_crc(pkg)) {
                Log.e(TAG, "pkg crc error");
                clearbuf = retry_max;
                return null;
            }
            int hd = pkg[0];
            pkg = unpack(pkg);
            if (pkg == null) {
                Log.e(TAG, "unpack data pkg error");
                clearbuf = retry_max;
                return null;
            }
//			Log.d(TAG, "handle data frame ok, length is " + pkg.length);		
            try {
                res.write(pkg);
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
                clearbuf = retry_max;
                return null;
            }
            if (hd == END_HEAD) {
                Log.d(TAG, "handle data pkg ok, total length is " + res.toByteArray().length);
                return res.toByteArray();
            }
        }
        while (true);
    }

    private byte[] handlepkg() {
        byte[] pkg = getframe();
        if (pkg == null) {
            Log.e(TAG, "don't get frame");
            return null;
        }
        pkg = decodepkg(pkg);
        if (!check_crc(pkg)) {
            Log.e(TAG, "pkg crc error");
            return null;
        }
        pkg = unpack(pkg);
        if (pkg == null) {
            Log.e(TAG, "unpack cmd pkg error");
            return null;
        }
        Log.d(TAG, "handle cmd pkg ok, length is " + pkg.length);
        return pkg;
    }

    /*****************************************************************************************************************************/
    public void fillbuf() {
        byte[] tmp = readport(fd, IO_BLOCK_SIZE, DEFAULT_DELAY);
        if (tmp != null) {
            if (clearbuf > 0) {
                clearbuf = retry_max;
//				Log.e(TAG, "clearbuf size is " + tmp.length);
                iobuf.reset();
                return;
            }
//			Log.d(TAG, "read thread has read " + tmp.length + " bytes @@@@@@@@@@@@@@@@@@@@@@@@@@");
            for (byte n : tmp) {
//				synchronized (iobuf) {
                iobuf.write(n);
//				}
            }
        } else {
            if (clearbuf > 0) {
//				Log.i(TAG, "clear null reatry is " + clearbuf);
                clearbuf--;
            }
        }
    }


    public int DetectFinger() {
        byte[] cmd = new byte[1], res;
        cmd[0] = 1;
        serialPort.WriteSerialByte(fd,packet(CMD_HEAD,cmd));
//        writeport(fd, packet(CMD_HEAD, cmd));
        res = handlepkg();
        if (res == null) {
            return -1;
        }
        return res[0];
    }

    public int[] GetImage() {
        byte[] cmd = new byte[1], res;
        cmd[0] = 2;
//        writeport(fd, packet(CMD_HEAD, cmd));
        serialPort.WriteSerialByte(fd, packet(CMD_HEAD, cmd));
        res = handlepkg();
        if ((res == null) || (res.length < 6)) {
            return null;
        }
        if (res[0] == 0) {
//			Log.d(TAG, "ValidArea is " + res[1]);
//			Log.d(TAG, "TB is " + res[2]);
//			Log.d(TAG, "BB is " + res[3]);
//			Log.d(TAG, "LB is " + res[4]);
//			Log.d(TAG, "RB is " + res[5]);
            int[] rval = new int[5];
            rval[0] = bti(res[1]);
            rval[1] = bti(res[2]);
            rval[2] = bti(res[3]);
            rval[3] = bti(res[4]);
            rval[4] = bti(res[5]);
            return rval;
        } else {
            Log.e(TAG, PrintErrorMsg((byte) res[0]));
            return null;
        }

    }

    public int GetTemplet(byte bufferid) {
        if ((bufferid != CHAR_BUFFER_A) && (bufferid != CHAR_BUFFER_B)) {
            Log.e(TAG, "invalid buffid");
            return -1;
        }
        byte[] cmd = new byte[2], res;
        cmd[0] = 3;
        cmd[1] = bufferid;
//        writeport(fd, packet(CMD_HEAD, cmd));
        serialPort.WriteSerialByte(fd, packet(CMD_HEAD, cmd));
        res = handlepkg();
        if (res == null) {
            return -1;
        }
        return res[0];
    }

    public int MoveTemplet(byte src, byte dest) {
        if ((src != CHAR_BUFFER_A) && (src != CHAR_BUFFER_B) && (src != MODEL_BUFFER)) {
            Log.e(TAG, "invalid src buffid");
            return -1;
        }
        if ((dest != CHAR_BUFFER_A) && (dest != CHAR_BUFFER_B) && (dest != MODEL_BUFFER) && (dest == src)) {
            Log.e(TAG, "invalid dest buffid");
            return -1;
        }
        byte[] cmd = new byte[3], res;
        cmd[0] = 0x20;
        cmd[1] = src;
        cmd[2] = dest;
//        writeport(fd, packet(CMD_HEAD, cmd));
        serialPort.WriteSerialByte(fd, packet(CMD_HEAD, cmd));
        res = handlepkg();
        if (res == null) {
            return -1;
        }
        return res[0];
    }

    public int[] MatchTwoTemplet() {
        byte[] cmd = new byte[1], res;
        cmd[0] = 4;
//        writeport(fd, packet(CMD_HEAD, cmd));
        serialPort.WriteSerialByte(fd, packet(CMD_HEAD, cmd));
        res = handlepkg();
        if ((res == null) || (res.length < 3)) {
            return null;
        }
        if (res[0] == 0) {
            int score = (bti(res[1]) << 8) + bti(res[2]);
//			Log.d(TAG, "score is " + score);
            int[] rval = new int[1];
            rval[0] = score;
            return rval;
        } else {
            Log.e(TAG, PrintErrorMsg((byte) res[0]));
            return null;
        }
    }

    public int[] SearchTemplet(byte bufferid, int start, int range) {
        if ((bufferid != CHAR_BUFFER_A) && (bufferid != CHAR_BUFFER_B) && (bufferid != MODEL_BUFFER)) {
            Log.e(TAG, "invalid buffid");
            return null;
        }
        if (((start + range) >= MAX_TEMPLET_STORE) || (start < 0) || (range <= 0)) {
            Log.e(TAG, "invalid search range");
            return null;
        }
        byte[] cmd = new byte[6], res;
        cmd[0] = 5;
        cmd[1] = bufferid;
        cmd[2] = (byte) ((start >> 8) & 0xff);
        cmd[3] = (byte) (start & 0xff);
        cmd[4] = (byte) ((range >> 8) & 0xff);
        cmd[5] = (byte) (range & 0xff);
//        writeport(fd, packet(CMD_HEAD, cmd));
        serialPort.WriteSerialByte(fd, packet(CMD_HEAD, cmd));
        res = handlepkg();
        if ((res == null) || (res.length < 35)) {
            return null;
        }
        if (res[0] == 0) {
            int templetid = (bti(res[1]) << 8) + bti(res[2]);
//			Log.d(TAG, "find templet " + res[1] + " " + res[2]);
            int[] rval = new int[1];
            rval[0] = templetid;
            return rval;
        } else {
            Log.e(TAG, PrintErrorMsg((byte) res[0]));
            return null;
        }
    }

    public int MergeTwoTemplet() {
        byte[] cmd = new byte[1], res;
        cmd[0] = 6;
//        writeport(fd, packet(CMD_HEAD, cmd));
        serialPort.WriteSerialByte(fd, packet(CMD_HEAD, cmd));
        res = handlepkg();
        if (res == null) {
            return -1;
        }
        return res[0];
    }

    public int StoreTemplet(byte bufferid, int pageid) {
        if ((bufferid != CHAR_BUFFER_A) && (bufferid != CHAR_BUFFER_B) && (bufferid != MODEL_BUFFER)) {
            Log.e(TAG, "invalid buffid");
            return -1;
        }
        if ((pageid >= MAX_TEMPLET_STORE) || (pageid < 0)) {
            Log.e(TAG, "invalid page id");
            return -1;
        }
        byte[] cmd = new byte[4], res;
        cmd[0] = 7;
        cmd[1] = bufferid;
        cmd[2] = (byte) ((pageid >> 8) & 0xff);
        cmd[3] = (byte) (pageid & 0xff);
//        writeport(fd, packet(CMD_HEAD, cmd));
        serialPort.WriteSerialByte(fd, packet(CMD_HEAD, cmd));
        res = handlepkg();
        if (res == null) {
            return -1;
        }
        return res[0];
    }

    public int LoadTemplet(int pageid) {
        if ((pageid >= MAX_TEMPLET_STORE) || (pageid < 0)) {
            Log.e(TAG, "invalid page id");
            return -1;
        }
        byte[] cmd = new byte[3], res;
        cmd[0] = 8;
        cmd[1] = (byte) ((pageid >> 8) & 0xff);
        cmd[2] = (byte) (pageid & 0xff);
//        writeport(fd, packet(CMD_HEAD, cmd));
        serialPort.WriteSerialByte(fd, packet(CMD_HEAD, cmd));
        res = handlepkg();
        if (res == null) {
            return -1;
        }
        return res[0];
    }

    public byte[] UpTemplet(byte bufferid) {
        if ((bufferid != CHAR_BUFFER_A) && (bufferid != CHAR_BUFFER_B) && (bufferid != MODEL_BUFFER)) {
            Log.e(TAG, "invalid buffid");
            return null;
        }
        byte[] cmd = new byte[2], res;
        cmd[0] = 9;
        cmd[1] = bufferid;
//        writeport(fd, packet(CMD_HEAD, cmd));
        serialPort.WriteSerialByte(fd, packet(CMD_HEAD, cmd));
        res = handlepkg();
        if (res == null) {
            return null;
        }
        if (res[0] == 0) {
            res = handledatapkg();
        } else {
            Log.e(TAG, PrintErrorMsg((byte) res[0]));
            res = null;
        }
        return res;
    }

    public int DownTemplet(byte bufferid, byte[] templet) {
        if ((bufferid != CHAR_BUFFER_A) && (bufferid != CHAR_BUFFER_B) && (bufferid != MODEL_BUFFER)) {
            Log.e(TAG, "invalid buffid");
            return -1;
        }
        if (templet.length != TEMPLET_LENGTH) {
            Log.e(TAG, "invalid buffer length");
            return -1;
        }
        byte[] cmd = new byte[2], res;
        cmd[0] = 0xA;
        cmd[1] = bufferid;
//        writeport(fd, packet(CMD_HEAD, cmd));
        serialPort.WriteSerialByte(fd, packet(CMD_HEAD, cmd));
        res = handlepkg();
        if (res == null) {
            return -1;
        }
        if (res[0] == 0) {
            Log.d("fps_n", "get ok to transfer continue data");
            int i;
            for (i = 0; i < (templet.length / 128 - 1); i++) {
                byte[] pk = new byte[128];
                System.arraycopy(templet, i * 128, pk, 0, pk.length);
//                writeport(fd, packet(DATA_HEAD, pk));        //**********************************************
                serialPort.WriteSerialByte(fd, packet(DATA_HEAD, pk));
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            }
            int rest = templet.length - i * 128;
            byte[] pk = new byte[rest];
            System.arraycopy(templet, i * 128, pk, 0, pk.length);
//            writeport(fd, packet(END_HEAD, pk));
            serialPort.WriteSerialByte(fd, packet(DATA_HEAD, pk));
            //**********************************************

            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
            return 0;
        } else {
            return res[0];
        }
    }

    public byte[] UpImage() {
        byte[] cmd = new byte[1], res;
        cmd[0] = 0xB;
//        writeport(fd, packet(CMD_HEAD, cmd));
        serialPort.WriteSerialByte(fd, packet(CMD_HEAD, cmd));
        res = handlepkg();
        if (res == null) {
            return null;
        }
        if (res[0] == 0) {
            res = handledatapkg();
        } else {
            Log.e(TAG, PrintErrorMsg((byte) res[0]));
            res = null;
        }
        return res;
    }

    public int DownImage(byte[] image) {
        if (image == null) {
            return -1;
        }
        if (image.length * 2 != img_height * img_width) {
            return -1;
        }
        byte[] cmd = new byte[1], res;
        cmd[0] = 0xC;
        writeport(fd, packet(CMD_HEAD, cmd));
        res = handlepkg();
        if (res == null) {
            return -1;
        }
        if (res[0] == 0) {
            Log.d("fps_n", "get ok to transfer continue data");
            int i;
            for (i = 0; i < (image.length / 128 - 1); i++) {
                byte[] pk = new byte[128];
                System.arraycopy(image, i * 128, pk, 0, pk.length);
                writeport(fd, packet(DATA_HEAD, pk));        //**********************************************
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            }
            int rest = image.length - i * 128;
            byte[] pk = new byte[rest];
            System.arraycopy(image, i * 128, pk, 0, pk.length);
            writeport(fd, packet(END_HEAD, pk));        //**********************************************
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
            return 0;
        } else {
            return res[0];
        }
    }

    public int DeletOneTemplet(int pageid) {
        if ((pageid >= MAX_TEMPLET_STORE) || (pageid < 0)) {
            Log.e(TAG, "invalid page id");
            return -1;
        }
        byte[] cmd = new byte[3], res;
        cmd[0] = 0xD;
        cmd[1] = (byte) ((pageid >> 8) & 0xff);
        cmd[2] = (byte) (pageid & 0xff);
//        writeport(fd, packet(CMD_HEAD, cmd));
        serialPort.WriteSerialByte(fd, packet(CMD_HEAD, cmd));
        res = handlepkg();
        if (res == null) {
            return -1;
        }
        return res[0];
    }

    public int EraseAllTemplet() {
        byte[] cmd = new byte[1], res;
        cmd[0] = 0xE;
//        writeport(fd, packet(CMD_HEAD, cmd));
        serialPort.WriteSerialByte(fd, packet(CMD_HEAD, cmd));
        res = handlepkg();
        if (res == null) {
            return -1;
        }
        return res[0];
    }

    public byte[] ReadParTab() {
        byte[] cmd = new byte[1], res;
        cmd[0] = 0xF;
        writeport(fd, packet(CMD_HEAD, cmd));
        res = handlepkg();
        if ((res == null) || (res.length < 51)) {
            return null;                    //##########################
        }
        if (res[0] == 0) {
            byte[] rval = new byte[50];
            System.arraycopy(res, 1, rval, 0, rval.length);
            return rval;
        } else {
            Log.e(TAG, PrintErrorMsg((byte) res[0]));
            return null;
        }
    }

    public int SetSecurLevel(byte level) {
        if ((level < 1) || (level > 5)) {
            Log.e(TAG, "invalid secure level");
            return -1;
        }
        byte[] cmd = new byte[2], res;
        cmd[0] = 0x12;
        cmd[1] = level;
        writeport(fd, packet(CMD_HEAD, cmd));
        res = handlepkg();
        if (res == null) {
            return -1;
        }
        return res[0];
    }

    public int SetPwd(byte[] passwd) {
        if (passwd.length != 4) {
            Log.e(TAG, "invalid password length");
            return -1;
        }
        byte[] cmd = new byte[5], res;
        cmd[0] = 0x13;
        cmd[1] = passwd[0];
        cmd[2] = passwd[1];
        cmd[3] = passwd[2];
        cmd[4] = passwd[3];
        writeport(fd, packet(CMD_HEAD, cmd));
        res = handlepkg();
        if (res == null) {
            return -1;
        }
        return res[0];
    }

    public int VfyPwd(byte[] passwd) {
        if (passwd.length != 4) {
            Log.e(TAG, "invalid password length");
            return -1;
        }
        byte[] cmd = new byte[5], res;
        cmd[0] = 0x14;
        cmd[1] = passwd[0];
        cmd[2] = passwd[1];
        cmd[3] = passwd[2];
        cmd[4] = passwd[3];
        writeport(fd, packet(CMD_HEAD, cmd));
        res = handlepkg();
        if (res == null) {
            return -1;
        }
        return res[0];
    }

    public int ResetDevice() {
        byte[] cmd = new byte[1], res;
        cmd[0] = 0x15;
        writeport(fd, packet(CMD_HEAD, cmd));
        res = handlepkg();
        if (res == null) {
            return -1;
        }
        if (res[0] == 0) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
            return 0;
        } else {
            return res[0];
        }
    }

    public int LEDCtrl(byte power) {
        if ((power != LIGHT_ON) && (power != LIGHT_OFF)) {
            Log.e(TAG, "error light status");
            return -1;
        }
        byte[] cmd = new byte[2], res;
        cmd[0] = 0x16;
        cmd[1] = power;
        writeport(fd, packet(CMD_HEAD, cmd));
        res = handlepkg();
        if (res == null) {
            return -1;
        }
        return res[0];
    }                                            //not support now

    public int WriteUserArea(byte[] data) {
        if ((data == null) || (data.length != 32)) {
            Log.e(TAG, "error user data");
            return -1;
        }
        byte[] cmd = new byte[33], res;
        cmd[0] = 0x23;
        System.arraycopy(data, 0, cmd, 1, data.length);
        writeport(fd, packet(CMD_HEAD, cmd));
        res = handlepkg();
        if (res == null) {
            return -1;
        }
        return res[0];
    }

    public byte[] ReadUserArea() {
        byte[] cmd = new byte[1], res;
        cmd[0] = 0x24;
        writeport(fd, packet(CMD_HEAD, cmd));
        res = handlepkg();
        if ((res == null) || (res.length < 33)) {
            return null;
        }
        if (res[0] == 0) {
            byte[] n = new byte[32];
            System.arraycopy(res, 1, n, 0, n.length);
            return n;
        } else {
            Log.e(TAG, PrintErrorMsg((byte) res[0]));
            return null;
        }
    }

    public int CheckTemplet(int pageid) {
        if ((pageid >= MAX_TEMPLET_STORE) || (pageid < 0)) {
            Log.e(TAG, "invalid page id");
            return -1;
        }
        byte[] cmd = new byte[3], res;
        cmd[0] = 0x28;
        cmd[1] = (byte) ((pageid >> 8) & 0xff);
        cmd[2] = (byte) (pageid & 0xff);
        writeport(fd, packet(CMD_HEAD, cmd));
        res = handlepkg();
        if (res == null) {
            return -1;
        }
        return res[0];
    }

    public int CheckDownStatus() {
        byte[] cmd = new byte[1], res;
        cmd[0] = 0x31;
        writeport(fd, packet(CMD_HEAD, cmd));
        res = handlepkg();
        if (res == null) {
            return -1;
        }
        return res[0];
    }

    public int SetFPLevel(byte level) {
        if ((level < 0) || (level > 2)) {
            Log.e(TAG, "set level failed");
            return -1;
        }
        byte[] cmd = new byte[2], res;
        cmd[0] = 0x32;
        cmd[1] = level;
        writeport(fd, packet(CMD_HEAD, cmd));
        res = handlepkg();
        if (res == null) {
            return -1;
        }
        return res[0];
    }

    public byte[] ChangeToBMP(byte[] img) {
        if (img.length * 2 != img_height * img_width) {
            Log.e(TAG, "img data is not complete");
            return null;
        }
//		System.gc();
        byte[] res = new byte[img.length + bmp_palette.length + bmp_head.length];
        System.arraycopy(bmp_head, 0, res, 0, bmp_head.length);
        System.arraycopy(bmp_palette, 0, res, bmp_head.length, bmp_palette.length);
        System.arraycopy(img, 0, res, bmp_head.length + bmp_palette.length, img.length);
        return res;
/*		byte[] res = new byte[img_width * img_height * 3 + 54];
        System.arraycopy(img_head, 0, res, 0, img_head.length);
		for(int i = 0; i < img.length; i++)
		{
			res[i * 6 + img_head.length] = (byte)(img[i] & 0xf0);
			res[i * 6 + img_head.length + 1] = (byte)(img[i] & 0xf0);
			res[i * 6 + img_head.length + 2] = (byte)(img[i] & 0xf0);
				
			res[i * 6 + img_head.length + 3] = (byte)((img[i] << 4) & 0xf0);
			res[i * 6 + img_head.length + 4] = (byte)((img[i] << 4) & 0xf0);
			res[i * 6 + img_head.length + 5] = (byte)((img[i] << 4) & 0xf0);
		}
		return res;*/
    }

    public String PrintErrorMsg(byte code) {
        String ray;
        switch (code) {
            case -1:
                ray = "communication error";
                break;
            case 0:
                ray = "All ok";
                break;
            case 1:
                ray = "recive package error";
                break;
            case 2:
                ray = "no finger on sensor";
                break;
            case 3:
                ray = "scan finger failed";
                break;
            case 4:
                ray = "fingerprint too dry or thin";
                break;
            case 5:
                ray = "fingerprint too wet or paste";
                break;
            case 6:
                ray = "fingerprint too chaos to get feature code";
                break;
            case 7:
                ray = "fingerprint don't have enough feature point";
                break;
            case 8:
                ray = "fingerprint don't match";
                break;
            case 9:
                ray = "don't search fingerprint";
                break;
            case 0xa:
                ray = "merge fingerprint failed";
                break;
            case 0xb:
                ray = "template lib address is overflow";
                break;
            case 0xc:
                ray = "read feature code from template lib failed";
                break;
            case 0xd:
                ray = "upload feature code failed";
                break;
            case 0xe:
                ray = "device can't recive followed data package ";
                break;
            case 0xf:
                ray = "upload image failed";
                break;
            case 0x10:
                ray = "delele template failed";
                break;
            case 0x11:
                ray = "clear template lib failed";
                break;
            case 0x12:
                ray = "can't sleep";
                break;
            case 0x13:
                ray = "password auth failed";
                break;
            case 0x14:
                ray = "system reset failed";
                break;
            case 0x15:
                ray = "image buffer don't contain valid fingerprint";
                break;
            case 0x17:
                ray = "finger on sensor too long";
                break;
            case 0x18:
                ray = "oper flash failed";
                break;
            case 0x19:
                ray = "there is no valid template on address";
                break;
            case 0x24:
                ray = "download failed";
                break;
            case 0x25:
                ray = "live body level auth failed";
                break;
            default:
                ray = "Unknow error";
                break;
        }
        return ray;
    }

/*	public void PrintErrorMsg(byte code)
	{
		switch(code)
		{
		case -1:
			Log.e(TAG, "communication error");
			break;
		case 0:
			Log.d(TAG, "All ok");
			break;
		case 1:
			Log.e(TAG, "recive package error");
			break;
		case 2:
			Log.e(TAG, "no finger on sensor");
			break;
		case 3:
			Log.e(TAG, "scan finger failed");
			break;
		case 4:
			Log.e(TAG, "fingerprint too dry or thin");
			break;
		case 5:
			Log.e(TAG, "fingerprint too wet or paste");
			break;
		case 6:
			Log.e(TAG, "fingerprint too chaos to get feature code");
			break;
		case 7:
			Log.e(TAG, "fingerprint don't have enough feature point");
			break;
		case 8:
			Log.e(TAG, "fingerprint don't match");
			break;
		case 9:
			Log.e(TAG, "don't search fingerprint");
			break;
		case 0xa:
			Log.e(TAG, "merge fingerprint failed");
			break;
		case 0xb:
			Log.e(TAG, "template lib address is overflow");
			break;
		case 0xc:
			Log.e(TAG, "read feature code from template lib failed");
			break;
		case 0xd:
			Log.e(TAG, "upload feature code failed");
			break;
		case 0xe:
			Log.e(TAG, "device can't recive followed data package ");
			break;
		case 0xf:
			Log.e(TAG, "upload image failed");
			break;
		case 0x10:
			Log.e(TAG, "delele template failed");
			break;
		case 0x11:
			Log.e(TAG, "clear template lib failed");
			break;
		case 0x12:
			Log.e(TAG, "can't sleep");
			break;
		case 0x13:
			Log.e(TAG, "password auth failed");
			break;
		case 0x14:
			Log.e(TAG, "system reset failed");
			break;
		case 0x15:
			Log.e(TAG, "image buffer don't contain valid fingerprint");
			break;
		case 0x17:
			Log.e(TAG, "finger on sensor too long");
			break;
		case 0x18:
			Log.e(TAG, "oper flash failed");
			break;
		case 0x19:
			Log.e(TAG, "there is no valid template on address");
			break;
		case 0x24:
			Log.e(TAG, "download failed");
			break;
		case 0x25:
			Log.e(TAG, "live body level auth failed");
			break;
		default:
			Log.e(TAG, "Unknow error");
			break;
		}
	}*/

    /********************************************************************************************************************************/

    private native int openport(String port);

    private native void closeport(int fd);

    private native byte[] readport(int fd, int count, int delay);

    private native int writeport(int fd, byte[] buf);

    private native void clearportbuf(int fd);

    static {
        System.loadLibrary("package");
        System.loadLibrary("fingerprint_port");
    }
}
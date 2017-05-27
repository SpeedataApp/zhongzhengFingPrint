package com.android.fplibs;

import android.util.Log;


public class RingByteBuffer{
		private byte[] buf;
		private int capacity;
		private int writepos;
		private int readpos;
		private int size;
		
		public RingByteBuffer(int psize)
		{
			buf = new byte[psize];
			capacity = psize;
			writepos = 0;
			readpos = 0;
			size = 0;
		}
		
		public int write(byte d)
		{
			if(size == capacity)
			{
				Log.e("RINGBUF", "buf is full $$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$$");
				return -1;
			}
			buf[writepos++] = d;
			size++;
			if(writepos == capacity)
			{
				writepos = 0;
			}
			return 0;
		}
		
		public int read()
		{
			if(size == 0)
			{
				return 1000;
			}
			byte res = buf[readpos++];
			size--;
			if(readpos == capacity)
			{
				readpos = 0;
			}
			return res;
		}
		
		public int get_size()
		{
			return size;
		}
		
		public int get_available()
		{
			return capacity - size;
		}
		
		public void reset()
		{
			writepos = 0;
			readpos = 0;
			size = 0;
		}
	}

package org.corfudb.runtime;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.corfudb.sharedlog.ClientLib;
import org.corfudb.sharedlog.CorfuException;

/**
 * @author mbalakrishnan
 *
 */
public class CorfuDBRuntime
{

	/**
	 * @param args
	 */
	public static void main(String[] args) throws Exception
	{
		System.out.println("Hello World!");

		//append a few entries to the stream
	/*	long ssid = 5;
		Stream smrstream = new DummyStreamImpl(ssid);
		smrstream.append(new LogEntry(("AAA").getBytes(), ssid));
		smrstream.append(new LogEntry(("BBB").getBytes(), ssid));
		smrstream.append(new LogEntry(("CCC").getBytes(), ssid));
		System.out.println(new String(smrstream.readNext().payload));
		System.out.println(new String(smrstream.readNext().payload));
		System.out.println(new String(smrstream.readNext().payload));*/

		ClientLib crf;

		try
		{
			crf = new ClientLib("http://localhost:8000/corfu");
		}
		catch (CorfuException e)
		{
			throw e;
		}

		CorfuDBRuntime TR = new CorfuDBRuntime(new DummyStreamFactoryImpl());

		Thread T = new Thread(new CorfuDBTester(TR));
		T.start();
	}

	Map<Long, CorfuDBObject> objectmap;
	Map<Long, Stream> streammap;
	StreamFactory sf;

	public void registerObject(CorfuDBObject to)
	{
		//todo: check if the object already exists
		synchronized(objectmap)
		{
			if(objectmap.containsKey(to.getID()))
			{
				System.out.println("object ID already registered!");
				throw new RuntimeException();
			}
			objectmap.put(to.getID(), to);
			streammap.put(to.getID(), sf.new_stream(to.getID()));
		}
	}

	public CorfuDBRuntime(StreamFactory tsf)
	{
		sf = tsf;
		objectmap = new HashMap<Long, CorfuDBObject>();
		streammap = new HashMap<Long, Stream>();
	}

	void queryhelper(long sid)
	{
		//later, asynchronously invoke the sync() thread
		sync(sid);
	}

	void updatehelper(byte[] update, long sid)
	{
		Stream curstream = streammap.get(sid);
		//todo: stream doesn't exist
		curstream.append(new LogEntry(update, sid));
	}

	void sync(long sid)
	{
		Stream curstream = streammap.get(sid);
		//todo: stream doesn't exist
		long curtail = curstream.checkTail();
		LogEntry update = curstream.readNext(curtail);
		while(update!=null)
		{
//			System.out.println(update.streamid);
			synchronized(objectmap)
			{
				objectmap.get(update.streamid).upcall(update.payload); //todo: check for non-existence
			}
			update = curstream.readNext(curtail);
		}
	}

}

class CorfuDBTester implements Runnable
{

	CorfuDBRuntime TR;
	public CorfuDBTester(CorfuDBRuntime tTR)
	{
		TR = tTR;
	}

	public void run()
	{
		System.out.println("starting thread");
		CorfuDBCounter tr = new CorfuDBCounter(TR, 1234);
		tr.increment();
		tr.increment();
		tr.increment();
		System.out.println("counter value = " + tr.read());
	}

}


interface CorfuDBObject
{
	public void upcall(byte[] update);
	public long getID();
}

class CorfuDBCounter implements CorfuDBObject
{
	int registervalue;
	CorfuDBRuntime TR;
	long oid;
	public long getID()
	{
		return oid;
	}
	public CorfuDBCounter(CorfuDBRuntime tTR, long toid)
	{
		registervalue = 0;
		TR = tTR;
		oid = toid;
		TR.registerObject(this);
	}
	public void upcall(byte update[])
	{
		//System.out.println("dummyupcall");
		if(update[0]==0) //increment
			registervalue++;
		else
			registervalue--;
		System.out.println("Setting value to " + registervalue);
	}
	public void increment()
	{
		//System.out.println("dummyinc");
		byte b[] = new byte[1]; //hardcoded
		b[0] = 0;
		TR.updatehelper(b, oid);
	}
	public void decrement()
	{
		//System.out.println("dummydec");
		byte b[] = new byte[1]; //hardcoded
		b[0] = 1;
		TR.updatehelper(b, oid);
	}
	public int read()
	{
		TR.queryhelper(oid);
		return registervalue;
	}

}

class CorfuDBRegister implements CorfuDBObject
{
	ByteBuffer converter;
	int registervalue;
	CorfuDBRuntime TR;
	long oid;
	public long getID()
	{
		return oid;
	}

	public CorfuDBRegister(CorfuDBRuntime tTR, long toid)
	{
		registervalue = 0;
		TR = tTR;
		converter = ByteBuffer.wrap(new byte[4]); //hardcoded
		oid = toid;
		TR.registerObject(this);
	}
	public void upcall(byte update[])
	{
//		System.out.println("dummyupcall");
		converter.put(update);
		converter.rewind();
		registervalue = converter.getInt();
		converter.rewind();
	}
	public void write(int newvalue)
	{
//		System.out.println("dummywrite");
		converter.putInt(newvalue);
		byte b[] = new byte[4]; //hardcoded
		converter.rewind();
		converter.get(b);
		converter.rewind();
		TR.updatehelper(b, oid);
	}
	public int read()
	{
//		System.out.println("dummyread");
		TR.queryhelper(oid);
		return registervalue;
	}
	public int readStale()
	{
		return registervalue;
	}
}






class LogEntry
{
	public long streamid;
	public byte[] payload;
	public LogEntry(byte[] tpayload, long tstreamid)
	{
		streamid = tstreamid;
		payload = tpayload;
	}
}

interface Stream
{
	long append(LogEntry le); //todo: should append be in the stream interface, since we'll eventually append to multiple streams?
	LogEntry readNext();
	LogEntry readNext(long stoppos);
	long checkTail();
}


interface StreamFactory
{
	public Stream new_stream(long streamid);
}

class DummyStreamFactoryImpl implements StreamFactory
{
	public Stream new_stream(long streamid)
	{
		return new DummyStreamImpl(streamid);
	}
}

class CorfuStreamFactoryImpl implements StreamFactory
{
	ClientLib cl;
	public CorfuStreamFactoryImpl(ClientLib tcl)
	{
		cl = tcl;
	}
	public Stream new_stream(long streamid)
	{
		return new CorfuStreamImpl(cl);
	}
}

class CorfuStreamImpl implements Stream
{
	ClientLib cl;
	public CorfuStreamImpl(ClientLib tcl)
	{
		cl = tcl;
	}

	@Override
	public long append(LogEntry le)
	{
		long ret;
		try
		{
			ret = cl.appendExtnt(le.payload, le.payload.length);
		}
		catch(CorfuException ce)
		{
			throw new RuntimeException(ce);
		}
		return ret;
	}

	@Override
	public LogEntry readNext()
	{
		return null;
	}

	@Override
	public LogEntry readNext(long stoppos)
	{
		System.out.println("unimplemented");
		return null;
	}

	@Override
	public long checkTail()
	{
		System.out.println("unimplemented");
		return 0;
	}
}

class DummyStreamImpl implements Stream
{
	ArrayList<LogEntry> log;
	long curpos; //first unread entry
	long curtail; //total number of entries in log

	public synchronized long append(LogEntry entry)
	{
//		System.out.println("Dummy append");
		log.add(entry);
		return curtail++;
	}

	public synchronized LogEntry readNext()
	{
		return readNext(0);
	}

	public synchronized LogEntry readNext(long stoppos)
	{
//		System.out.println("Dummy read");
		if(curpos<curtail && (stoppos==0 || curpos<stoppos))
		{
			LogEntry ret = log.get((int)curpos);
			curpos++;
			return ret;
		}
		return null;
	}

	public synchronized long checkTail()
	{
		return curtail;
	}

	public DummyStreamImpl(long streamid)
	{
		log = new ArrayList<LogEntry>();
		curpos = 0;
		curtail = 0;
	}
}
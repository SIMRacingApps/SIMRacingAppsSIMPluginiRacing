package com.SIMRacingApps.SIMPlugins.iRacing;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Calendar;
import java.util.Date;
/**
 * @author Jeffrey Gilliam
 * @copyright Copyright (C) 2015 - 2019 Jeffrey Gilliam
 * @since 1.0
 * @license Apache License 2.0
 */

public class SubHeader {
//    #// sub header used when writing telemetry to disk
//    #struct irsdk_diskSubHeader
//    #{
//    #    time_t sessionStartDate;
//    #    double sessionStartTime;
//    #    double sessionEndTime;
//    #    int sessionLapCount;
//    #    int sessionRecordCount;
//    #};

    public final static int SIZEOF_SUBHEADER = 8+8+8+4+4;

    private ByteBuffer m_bytebuffer;

    private void _copy(ByteBuffer buffer) {
        m_bytebuffer = ByteBuffer.allocate(SIZEOF_SUBHEADER);
        m_bytebuffer.position(0);
        buffer.position(0);
        m_bytebuffer.put(buffer);
        m_bytebuffer.order(ByteOrder.LITTLE_ENDIAN);

        //if values are zero, then default to today
        if (getSessionStartDate() == 0L) {
            setSessionStartDate((int)(System.currentTimeMillis()/1000L));
            Calendar today = Calendar.getInstance();
            today.setTime(new Date());
            setSessionStartTime(
                (double)(today.get(Calendar.HOUR_OF_DAY)*60*60) +
                (double)(today.get(Calendar.MINUTE)*60) +
                (double)(today.get(Calendar.SECOND)) +
                (double)(today.get(Calendar.MILLISECOND)) / 1000.0
            );
        }
    }

    public SubHeader() {
        byte buffer[] = new byte[SIZEOF_SUBHEADER];
        _copy(ByteBuffer.wrap(buffer));
    }

    public SubHeader(ByteBuffer buffer) {
        //make a copy of the buffer
    	_copy(buffer);
    }

    public byte[] _getByteArray()                     { return m_bytebuffer.array();       }
    public long getSessionStartDate()                 { return m_bytebuffer.getLong(0);    }
    public double getSessionStartTime()               { return m_bytebuffer.getDouble(8);  }
    public double getSessionEndTime()                 { return m_bytebuffer.getDouble(16); }
    public int getSessionLapCount()                   { return m_bytebuffer.getInt(24);    }
    public int getSessionRecordCount()                { return m_bytebuffer.getInt(28);    }

    public void setSessionStartDate(int v)            { m_bytebuffer.putInt(0,v);    }
    public void setSessionStartDate(Date v)           {
                                                        int i = (int)(v.getTime() / 1000L);
                                                        m_bytebuffer.putInt(0,i);
                                                      }
    public void setSessionStartTime(double v)         { m_bytebuffer.putDouble(8,v);  }
    public void setSessionStartTime(Date v)           {
                                                        Calendar today = Calendar.getInstance();
                                                        today.setTime(v);
                                                        double d = (
                                                            (double)(today.get(Calendar.HOUR_OF_DAY)*60*60) +
                                                            (double)(today.get(Calendar.MINUTE)*60) +
                                                            (double)(today.get(Calendar.SECOND)) +
                                                            (double)(today.get(Calendar.MILLISECOND)) / 1000.0
                                                        );
                                                        setSessionStartTime(d);
                                                    }
    public void setSessionEndTime(double v)           { m_bytebuffer.putDouble(16,v);    }
    public void setSessionEndTime(Date v)             {
                                                        Calendar today = Calendar.getInstance();
                                                        today.setTime(v);
                                                        double d = (
                                                            (double)(today.get(Calendar.HOUR_OF_DAY)*60*60) +
                                                            (double)(today.get(Calendar.MINUTE)*60) +
                                                            (double)(today.get(Calendar.SECOND)) +
                                                            (double)(today.get(Calendar.MILLISECOND)) / 1000.0
                                                        );
                                                        setSessionEndTime(d);
                                                      }
    public void setSessionLapCount(int v)             { m_bytebuffer.putInt(24,v);    }
    public void setSessionRecordCount(int v)          { m_bytebuffer.putInt(28,v);    }

}

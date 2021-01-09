package com.SIMRacingApps.SIMPlugins.iRacing;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Map;
import java.util.Map.Entry;



import java.util.TreeMap;

import com.SIMRacingApps.Server;
import com.SIMRacingApps.SIMPlugins.iRacing.VarHeaders.VarHeader;
/**
 * @author Jeffrey Gilliam
 * @copyright Copyright (C) 2015 - 2021 Jeffrey Gilliam
 * @since 1.0
 * @license Apache License 2.0
 */

public class Vars {

    private VarHeaders m_varheaders;
    private ByteBuffer m_bytebuffer;

    public Vars( VarHeaders varheaders, ByteBuffer buffer) {
        m_varheaders = varheaders;

        //make a copy of the buffer
        m_bytebuffer = ByteBuffer.allocate(buffer.capacity());
        m_bytebuffer.order(ByteOrder.LITTLE_ENDIAN);
        m_bytebuffer.position(0);
        buffer.position(0);
        m_bytebuffer.put(buffer);

    }

    public boolean getBoolean(String name,int index) throws IndexOutOfBoundsException {
        VarHeaders.VarHeader varheader = m_varheaders.getVarHeader(name);
        boolean b = false;

        if (varheader != null) {
            if (varheader.Type.getType() == VarType.irsdk_bool) {
                if (index < 0 || index >= varheader.Count)
                    throw new IndexOutOfBoundsException(name+"("+index+")");

                int i = m_bytebuffer.get(varheader.Offset + (varheader.Type.getSize() * index));
                b = (i == 1 ? true : false); //NOTE: a value of 256 is false because it means invalid. Specifically look for a 1 for true.
            }
        }
        return b;
    }
    public boolean getBoolean(String name) {    try {return getBoolean(name,0);} catch (IndexOutOfBoundsException e) {Server.logStackTrace(e);} return false; }

    public int getInteger(String name,int index) throws IndexOutOfBoundsException {
        VarHeaders.VarHeader varheader = m_varheaders.getVarHeader(name);
        int i = -1;

        if (varheader != null) {
            if (varheader.Type.getType() == VarType.irsdk_bitField
            ||  varheader.Type.getType() == VarType.irsdk_int
            ||  varheader.Type.getType() == VarType.irsdk_bool
            ) {
                if (index < 0 || index >= varheader.Count)
                    throw new IndexOutOfBoundsException(name+"("+index+")");

                if (varheader.Type.getType() == VarType.irsdk_bool) {
                    i = m_bytebuffer.get(varheader.Offset + (varheader.Type.getSize() * index));
                    if (i < 0 || i > 1)
                        i = -1;
                }
                else {
                    i = m_bytebuffer.getInt(varheader.Offset + (varheader.Type.getSize() * index));
                }
            }
        }
        return i;
    }
    public int getInteger(String name) {    try {return getInteger(name,0);} catch (IndexOutOfBoundsException e) {Server.logStackTrace(e);} return 0; }
    public int getBitfield(String name,int index) throws IndexOutOfBoundsException {    return getInteger(name,index); }
    public int getBitfield(String name) {    try {return getInteger(name,0);} catch (IndexOutOfBoundsException e) {Server.logStackTrace(e);} return 0; }

    public float getFloat(String name,int index) throws IndexOutOfBoundsException {
        VarHeaders.VarHeader varheader = m_varheaders.getVarHeader(name);
        float f = Float.NaN;

        if (varheader != null) {
            if (varheader.Type.getType() == VarType.irsdk_bitField
            ||  varheader.Type.getType() == VarType.irsdk_int
            ||  varheader.Type.getType() == VarType.irsdk_bool
            ) {
                f = new Float(getInteger(name,index));
            }
            else
            if (varheader.Type.getType() == VarType.irsdk_float) {
                f = m_bytebuffer.getFloat(varheader.Offset + (varheader.Type.getSize() * index));

                //fix problem where LapDistPct is sometimes negative and 1 or above while on the track.
//removed this so the calling code can use the fact it went above 100%
//                if (name.equals("LapDistPct") || name.equals("CarIdxLapDistPct")) {
//                    if (f < 0.0 && f > -0.99) {
//                        f = f + 1.0f;
//                    } else {
//                        if (f >= 1.0) {
//                            f = f - 1.0f;
//                        }
//                    }
//                }
            }
        }
        return Float.isNaN(f) ? -1.0f : f;
    }
    public float getFloat(String name) {    try {return getFloat(name,0);} catch (IndexOutOfBoundsException e) {Server.logStackTrace(e);} return 0.0f; }

    public double getDouble(String name,int index) throws IndexOutOfBoundsException {
        VarHeaders.VarHeader varheader = m_varheaders.getVarHeader(name);
        double d = Double.NaN;

        if (varheader != null) {
            if (varheader.Type.getType() == VarType.irsdk_bitField
            ||  varheader.Type.getType() == VarType.irsdk_int
            ||  varheader.Type.getType() == VarType.irsdk_bool
            ) {
                d = (double)getInteger(name,index);
            }
            else
            if (varheader.Type.getType() == VarType.irsdk_float) {
                d = (double)getFloat(name,index);
            }
            else
            if (varheader.Type.getType() == VarType.irsdk_double) {
                d = m_bytebuffer.getDouble(varheader.Offset + (varheader.Type.getSize() * index));
            }
        }
        return Double.isNaN(d) ? -1.0 : d;
    }
    public double getDouble(String name) {    try {return getDouble(name,0);} catch (IndexOutOfBoundsException e) {Server.logStackTrace(e);} return 0.0; }

    public String getString(String name,int index,boolean unitflag) throws IndexOutOfBoundsException {
        VarHeaders.VarHeader varheader = m_varheaders.getVarHeader(name);
        String s = "0";  //set to zero so numeric conversions won't throw a not a number exception

        if (varheader != null) {
            if (varheader.Type.getType() == VarType.irsdk_bitField) {
                int i = getInteger(name,index);
                s = String.format("0x%H, 0b%s",i,Integer.toBinaryString(i));
            }
            else
            if (varheader.Type.getType() == VarType.irsdk_int) {
                int i = getInteger(name,index);
                s = String.format("%d",i);
            }
            else
            if (varheader.Type.getType() == VarType.irsdk_bool) {
                boolean b = getBoolean(name,index);
                s = b ? "1" : "0"; //String.valueOf(b);
            }
            else
            if (varheader.Type.getType() == VarType.irsdk_float) {
                float f = getFloat(name,index);
                s = String.format("%.8f",f);
            }
            else
            if (varheader.Type.getType() == VarType.irsdk_double) {
                double d = getDouble(name,index);
                s = String.format("%.16f",d);
            }
            else
            if (varheader.Type.getType() == VarType.irsdk_char) {
                //NOTE: char types do not come in arrays. Count is the length of the field
                byte buffer[] = new byte[varheader.Count];
                m_bytebuffer.get(buffer,varheader.Offset,buffer.length);
                s = (new String(buffer)).replaceAll("[\000]", "");
            }

            if (unitflag && !varheader.Unit.isEmpty()) s += " "+varheader.Unit;
        }
        return s;
    }

    public String getString(String name,int index) throws IndexOutOfBoundsException { return getString(name,index,false); }
    public String getString(String name,boolean unitflag) { try {return getString(name,0,unitflag);} catch (IndexOutOfBoundsException e) {Server.logStackTrace(e);} return "0"; }
    public String getString(String name) { try {return getString(name,0,false);} catch (IndexOutOfBoundsException e) {Server.logStackTrace(e);} return "0"; }

    public String toString() {
    	StringBuffer s = new StringBuffer("");
    	for (Entry<String,VarHeader> entry : m_varheaders.getVarHeaders().entrySet()) {
    		String name = entry.getKey();
    		VarHeader varheader = entry.getValue();

    		s.append(name);
    		s.append("(");
    		s.append(varheader.Type.getTypeName());
    		if (!varheader.Unit.isEmpty()) {
    			s.append(",");
    			s.append(varheader.Unit);
    		}
    		s.append(")");
    		s.append("=");
            if (varheader.Type.getType() != VarType.irsdk_char && varheader.Count > 1) {
            	for (int i=0; i < varheader.Count; i++) {
            		s.append("[");
            		s.append(Integer.toString(i));
            		s.append("]=");
            		s.append(getString(name,i));
            		s.append(",");
            	}
            }
            else {
            	s.append(getString(name,0));
    			s.append(",");
    			s.append(varheader.Desc);
            }
            s.append("\n");
    	}

    	return s.toString();

    }

    public Map<String,Object> getVars(String ...args) {
        Map<String,Object> m = new TreeMap<String,Object>();

        for (Entry<String,VarHeader> entry : m_varheaders.getVarHeaders().entrySet()) {
            String name = entry.getKey();
            VarHeader varheader = entry.getValue();
            int index = -1;

            if (args.length > 0) {
                name = args[0];
                varheader = m_varheaders.getVarHeader(name);
                if (varheader == null)
                    return m;

                if (args.length > 1)
                    index = Integer.parseInt(args[1]);
            }

            if (varheader.Type.getType() != VarType.irsdk_char && varheader.Count > 1) {
                if (varheader.Type.getType() == VarType.irsdk_bitField) {
                    if (index > -1) {
                        m.put(name, getInteger(name,index));
                        return m;
                    }
                    int[] i = new int[varheader.Count];
                    for (int j=0; j < varheader.Count; j++)
                        i[j] = getInteger(name,j);
                    m.put(name, i);
                }
                else
                if (varheader.Type.getType() == VarType.irsdk_int) {
                    if (index > -1) {
                        m.put(name, getInteger(name,index));
                        return m;
                    }
                    int[] b = new int[varheader.Count];
                    for (int j=0; j < varheader.Count; j++)
                        b[j] = getInteger(name,j);
                    m.put(name, b);
                }
                else
                if (varheader.Type.getType() == VarType.irsdk_bool) {
                    if (index > -1) {
                        m.put(name, getBoolean(name,index) ? 1 : 0);
                        return m;
                    }
                    int[] b = new int[varheader.Count];
                    for (int j=0; j < varheader.Count; j++)
                        b[j] = getBoolean(name,j) ? 1 : 0;
                    m.put(name, b);
                }
                else
                if (varheader.Type.getType() == VarType.irsdk_float) {
                    if (index > -1) {
                        m.put(name, (double)getFloat(name,index));
                        return m;
                    }
                    double[] f = new double[varheader.Count];
                    for (int j=0; j < varheader.Count; j++)
                        f[j] = (double)getFloat(name,j);
                    m.put(name, f);
                }
                else
                if (varheader.Type.getType() == VarType.irsdk_double) {
                    if (index > -1) {
                        m.put(name, getDouble(name,index));
                        return m;
                    }
                    double[] d = new double[varheader.Count];
                    for (int j=0; j < varheader.Count; j++)
                        d[j] = getDouble(name,j);
                    m.put(name, d);
                }
            }
            else {
                if (varheader.Type.getType() == VarType.irsdk_bitField) {
                    int i = getInteger(name,0);
                    m.put(name, i);
                }
                else
                if (varheader.Type.getType() == VarType.irsdk_int) {
                    int i = getInteger(name,0);
                    m.put(name, i);
                }
                else
                if (varheader.Type.getType() == VarType.irsdk_bool) {
                    boolean b = getBoolean(name,0);
                    m.put(name, b ? 1 : 0);
                }
                else
                if (varheader.Type.getType() == VarType.irsdk_float) {
                    float f = getFloat(name,0);
                    m.put(name, (double)f);
                }
                else
                if (varheader.Type.getType() == VarType.irsdk_double) {
                    double d = getDouble(name,0);
                    m.put(name, d);
                }
                else
                if (varheader.Type.getType() == VarType.irsdk_char) {
                    //NOTE: char types do not come in arrays. Count is the length of the field
                    byte buffer[] = new byte[varheader.Count];
                    m_bytebuffer.get(buffer,varheader.Offset,buffer.length);
                    String s = (new String(buffer)).replaceAll("[\000]", "");
                    m.put(name,s);
                }

                if (args.length > 0)
                    return m;
            }
        }

        return m;

    }
}

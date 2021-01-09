package com.SIMRacingApps.SIMPlugins.iRacing;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.*;
import java.util.Map.Entry;
import java.util.logging.Logger;

/**
 * Copyright (c) 2013, iRacing.com Motorsport Simulations, LLC.
 * All rights reserved.
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 * @author Jeffrey Gilliam
 * @copyright Copyright (C) 2015 - 2021 Jeffrey Gilliam
 * @since 1.0
 * @license Apache License 2.0
 */
public class VarHeaders {
    
    
//    # struct irsdk_varHeader
//    # {
//        # int type;            // irsdk_VarType
//        # int offset;          // offset fron start of buffer row
//        # int count;           // number of entrys (array)
//                             # // so length in bytes would be irsdk_VarTypeBytes[type] * count
//        # int pad[1];          // (16 byte align)
//
//        # char name[IRSDK_MAX_STRING];
//        # char desc[IRSDK_MAX_DESC];
//        # char unit[IRSDK_MAX_STRING];    // something like "kg/m^2"
//    # };

    public final static int NUMBER_OF_FIELDS = 4;
    public final static int SIZEOF_FIELDS    = 4;
    public final static int SIZEOF_VARHEADER = (NUMBER_OF_FIELDS * SIZEOF_FIELDS) + VarType.IRSDK_MAX_STRING + VarType.IRSDK_MAX_DESC + VarType.IRSDK_MAX_STRING;

    public class VarHeader {
        public String    Name;
        public String    Desc;
        public String    Unit;
        public int       Offset;
        public int       Count;
        public VarType   Type;
        public Object    Value;
    }

    private ByteBuffer m_bytebuffer;
    private Map<String,VarHeader> m_data;

    public VarHeaders(Header header,ByteBuffer buffer) {
        //make a copy of the buffer
        m_bytebuffer = ByteBuffer.allocate(SIZEOF_VARHEADER * header.getNumVars());
        m_bytebuffer.order(ByteOrder.LITTLE_ENDIAN);
        m_bytebuffer.position(0);
        buffer.position(0);
        m_bytebuffer.put(buffer);

        m_data = new TreeMap<String,VarHeader>();

        for (int i=0; i < header.getNumVars(); i++) {
            int    varoffset = i * SIZEOF_VARHEADER;
            VarHeader varheader = new VarHeader();
            byte transferbuffer[];

            varheader.Type         = new VarType(m_bytebuffer.getInt(varoffset + 0));
            varheader.Offset    = m_bytebuffer.getInt(varoffset + 4);
            varheader.Count        = m_bytebuffer.getInt(varoffset + 8);

            transferbuffer = new byte[VarType.IRSDK_MAX_STRING];
            m_bytebuffer.position(varoffset + 16);
            m_bytebuffer.get(transferbuffer,0,transferbuffer.length);
            varheader.Name        = (new String(transferbuffer)).replaceAll("[\000]", "");

            m_bytebuffer.position(varoffset + 16 + VarType.IRSDK_MAX_STRING + VarType.IRSDK_MAX_DESC);
            m_bytebuffer.get(transferbuffer,0,transferbuffer.length);
            varheader.Unit        = (new String(transferbuffer)).replaceAll("[\000]", "");

            transferbuffer = new byte[VarType.IRSDK_MAX_DESC];
            m_bytebuffer.position(varoffset + 16 + VarType.IRSDK_MAX_STRING);
            m_bytebuffer.get(transferbuffer,0,transferbuffer.length);
            varheader.Desc        = (new String(transferbuffer)).replaceAll("[\000]", "");

            varheader.Value = null; //to be filled in later
            
            //now put it in the cache
            m_data.put(varheader.Name, varheader);
        }
    }

    public Map<String,VarHeader> getVarHeaders() {
        return m_data;
    }

    public VarHeader getVarHeader(String name) {
        VarHeader v = m_data.get(name);
//        if (v == null) {
//        	//This is a list of valid names in later releases that are not available in some
//        	//of my recorded files. Do not log an error for these.
//            if (!name.equals("WaterPress")
//            &&  !name.contains("wear")
//            &&  !name.contains("temp")
//            &&  !name.contains("coldPressure")
//            &&  !name.contains("LapDeltaToSessionLast")
//            &&  !name.equals("WindshieldTearoff") //not in any build, virtual in my logic
//            &&  !name.isEmpty()
//            //from build_2014_10_21
//            &&  !name.equals("IsOnTrackCar")
//            &&  !name.equals("dcQTape") //not on all cars, ...stockcars2_chevy (National)
//            &&  !name.equals("dpWedgeAdj") //not on all cars, street stock
//            ) {
//                Server.logger().warning(String.format("VarHeaders.getVarHeader(%s) returned null", name));
//            }
//        }

        return v;
    }
    
    public Map<String,VarHeader> getVarHeaderValues(Vars vars) {
        //add in the var value 
        Iterator<Entry<String, VarHeader>> itr = m_data.entrySet().iterator();
        while (itr.hasNext()) {
            VarHeader header = itr.next().getValue();
            header.Value = vars.getVars(header.Name).get(header.Name); 
        }
        return m_data;
    }
    
    public VarHeader getVarHeaderValue(String name, Vars vars) {
        VarHeader v = getVarHeader(name);
        if (v != null) {
            v.Value = vars.getVars(v.Name).get(v.Name);
        }

        return v;
    }
}

package com.SIMRacingApps.SIMPlugins.iRacing.IODrivers;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.SIMRacingApps.Server;
import com.SIMRacingApps.SIMPlugins.iRacing.*;

public class IBTFileIODriver extends IODriver {

    
    private FileInputStream m_fis;
    private BufferedInputStream m_bis;
    private DataInputStream m_dis;

    @SuppressWarnings("unused")
    private IBTFileIODriver() {}    //no default constructor allowed

    public IBTFileIODriver(String play) throws FileNotFoundException {
        java.io.File file = new java.io.File(play);
        m_fis = new FileInputStream(file);
        m_bis = new BufferedInputStream(m_fis);
        m_dis = new DataInputStream(m_bis);
    }

    private byte m_subheader_buffer[] = null;

    public boolean Read(long startingtick,long endingtick) {

        try {
            byte header_buffer[] = null;
            byte sessioninfo_buffer[] = null;
            byte varheaders_buffer[] = null;
            byte var_buffer[] = null;

            while (true) {
                int len = Header.SIZEOF_HEADER;
                byte buffer[] = new byte[len];

                if (m_subheader_buffer == null) {    //there's only one header in an IBT file.
                    int bytesread = 0;
                    if (m_dis.read(buffer) == len) {
                        bytesread += len;
                        header_buffer = buffer;
                        Header header = new Header(ByteBuffer.wrap(header_buffer));

                        len = SubHeader.SIZEOF_SUBHEADER;
                        buffer = new byte[len];
                        if (m_dis.read(buffer) == len) {
                            bytesread += len;
                            m_subheader_buffer = buffer;

                            header.getSubHeader(new SubHeader(ByteBuffer.wrap(m_subheader_buffer)));

                            Server.logger().info(String.format(" subheader size=%d",SubHeader.SIZEOF_SUBHEADER));
                            Server.logger().info(String.format(" subheader_sessionStartDate(%d)",header.getSubHeader().getSessionStartDate())); //POSIX::strftime("%c",localtime($subheader->SessionStartDate));
                            Server.logger().info(String.format(" subheader sessionStartTime(%f)",header.getSubHeader().getSessionStartTime()));
                            Server.logger().info(String.format(" subheader sessionEndTime(%f)",header.getSubHeader().getSessionEndTime()));
                            Server.logger().info(String.format(" subheader sessionLapCount(%d)",header.getSubHeader().getSessionLapCount()));
                            Server.logger().info(String.format(" subheader sessionRecordCount(%d)",header.getSubHeader().getSessionRecordCount()));

                            //header Size = 112
                            //SubHeader Size = 32
                            //header.VarHeaderOffset() = 144
                            //header.SessionInfoOffset() = 18864
                            //header.VarBuf_BufOffset(0) = 31805

                            //Seek to the VarHeaderOffset position by reading data
                            if (bytesread < header.getVarHeaderOffset()) {
                                buffer = new byte[header.getVarHeaderOffset() - bytesread];
                                bytesread += m_dis.read(buffer);
                            }

                            len = VarHeaders.SIZEOF_VARHEADER * header.getNumVars();
                            buffer = new byte[len];
                            if (m_dis.read(buffer) == len) {
                                bytesread += len;
                                varheaders_buffer = buffer;

                                //Seek to the SessionInfoOffset position by reading data
                                if (bytesread < header.getSessionInfoOffset()) {
                                    buffer = new byte[header.getSessionInfoOffset() - bytesread];
                                    bytesread += m_dis.read(buffer);
                                }

                                len = header.getSessionInfoLen();
                                buffer = new byte[len];
                                if (m_dis.read(buffer) == len) {
                                    bytesread += len;
                                    sessioninfo_buffer = buffer;

                                    //Seek to the VarBuf_BufOffset position by reading data
                                    if (bytesread < header.getVarBuf_BufOffset(header.getNumBuf())) {
                                        buffer = new byte[header.getVarBuf_BufOffset(header.getNumBuf()) - bytesread];
                                        bytesread += m_dis.read(buffer);
                                    }

                                    super.ProcessBuffers(
                                             ByteBuffer.wrap(header_buffer)
                                        ,    ByteBuffer.wrap(sessioninfo_buffer)
                                        ,    ByteBuffer.wrap(varheaders_buffer)
                                        ,    null
                                        ,    ByteBuffer.wrap(m_subheader_buffer)
                                        );

//                                    getHeader().getSubHeader(m_subheader);
                                }
                            }
                        }
                    }
                }
                else {
                    //increment the tick in the header
                    getHeader().getLatest_VarBufTick(getHeader().getLatest_VarBufTick()+1);
                }

                //now read the vars, that's all that's left
                len = getHeader().getBufLen();
                buffer = new byte[len];
                if (m_dis.read(buffer) == len) {
                    var_buffer = buffer;
                    if (super.ProcessBuffers(
                             header_buffer      == null ? null : ByteBuffer.wrap(header_buffer)
                        ,    sessioninfo_buffer == null ? null : ByteBuffer.wrap(sessioninfo_buffer)
                        ,    varheaders_buffer  == null ? null : ByteBuffer.wrap(varheaders_buffer)
                        ,    ByteBuffer.wrap(var_buffer)
                        ,    m_subheader_buffer == null ? null : ByteBuffer.wrap(m_subheader_buffer)
                        )
                    ) {
//                        getHeader().getSubHeader(m_subheader);
                        //if between the ticks, get caught up if needed and return true.
                        if (getHeader().getLatest_VarBufTick() >= startingtick && getHeader().getLatest_VarBufTick() <= endingtick) {
                            return true;
                        }
                    }

                    //if we've gone past the ending tick, then break the loop to return EOF
                    if (getHeader().getLatest_VarBufTick() == 0 || getHeader().getLatest_VarBufTick() > endingtick) {
                        break;
                    }
                }
                else {
                    break;
                }
            }
        }
        catch (IOException e) {
        }

        isEOF(true);

        byte buffer[] = new byte[Header.SIZEOF_HEADER];
        super.ProcessBuffers(ByteBuffer.wrap(buffer), null, null, null, null);
        return false;
    }

    public void close() {
    	try {
			m_dis.close();
		} catch (IOException e) {
            Server.logStackTrace(Level.SEVERE,"IOException",e);
		}
    	super.close();
    }
}

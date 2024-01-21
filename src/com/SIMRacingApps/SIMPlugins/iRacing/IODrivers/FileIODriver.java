package com.SIMRacingApps.SIMPlugins.iRacing.IODrivers;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Date;
import java.util.logging.Level;

import com.SIMRacingApps.Server;
import com.SIMRacingApps.SIMPlugins.iRacing.*;
import com.SIMRacingApps.Util.FindFile;
import com.SIMRacingApps.servlets.SIMRacingApps;

/**
 * @author Jeffrey Gilliam
 * @copyright Copyright (C) 2015 - 2024 Jeffrey Gilliam
 * @since 1.0
 * @license Apache License 2.0
 */
public class FileIODriver extends IODriver {

    
    private InputStream m_fis;
    private BufferedInputStream m_bis;
    private DataInputStream m_dis;

    byte m_int_bytebuffer[] = new byte[4];
    ByteBuffer m_int_buffer;
    byte m_header_buffer[] = null;
    SubHeader m_subheader = null;
    byte m_sessioninfo_buffer[] = null;
    byte m_varheader_buffer[] = null;
    byte m_var_buffer[] = null;
    boolean m_caughtup = false;

    @SuppressWarnings("unused")
    private FileIODriver() {}    //no default constructor allowed

    public FileIODriver(String play) throws FileNotFoundException {
//don't know why, but this causes problems. Using old way for now.        
//        FindFile findfile = new FindFile(play);
//        File file = findfile.getFile();
//        m_fis = findfile.getInputStream();
        
        File file = new File(play);
        
        try {
            m_fis = new FileInputStream(file);
        }
        catch (FileNotFoundException e) {
            m_fis = SIMRacingApps.class.getClassLoader().getResourceAsStream(play);
            if (m_fis == null)
                throw e;
        }
        
        m_bis = new BufferedInputStream(m_fis);
        m_dis = new DataInputStream(m_bis);
        Server.logger().info(String.format("Playing from (%s)",play));
        //for files with no subheader, create a default using the file's time stamp
        m_subheader = new SubHeader();
        m_subheader.setSessionStartDate(new Date(file.lastModified()));
        m_subheader.setSessionStartTime(new Date(file.lastModified()));

        m_int_buffer = ByteBuffer.wrap(m_int_bytebuffer);
        m_int_buffer.order(ByteOrder.LITTLE_ENDIAN);
    }


    public boolean Read(long startingtick,long endingtick) {
        byte recordtypebuffer[] = new byte[4];
        String recordtype;

        try {
            if (m_dis.read(recordtypebuffer) == recordtypebuffer.length) {
                recordtype = new String(recordtypebuffer);

                byte header_buffer[] = null;
                byte sessioninfo_buffer[] = null;
                byte varheader_buffer[] = null;
                byte var_buffer[] = null;

                while (!recordtype.equals(IODriver.EOF_TAG)) {

                    if (!recordtype.equals(IODriver.HEADER_TAG)
                    &&  !recordtype.equals(IODriver.SUBHEADER_TAG)
                    &&  !recordtype.equals(IODriver.SESSIONINFO_TAG)
                    &&  !recordtype.equals(IODriver.VARHEADERS_TAG)
                    &&  !recordtype.equals(IODriver.VARS_TAG)
                    &&  !recordtype.equals(IODriver.COMMAND_TAG)
                    &&  !recordtype.equals(IODriver.OPTIONS_TAG)
                    ) {
                        Server.logger().severe(String.format("Playback file is corrupted, [%s]",recordtype));
                        System.exit(1);
                    }

                    if (m_dis.read(m_int_bytebuffer) == m_int_bytebuffer.length) {
                        int len = m_int_buffer.getInt(0);
                        byte buffer[] = new byte[len];
                        if (m_dis.read(buffer) == len) {

                            if (recordtype.equals(IODriver.COMMAND_TAG)) {
                                this.setCommand(new String(buffer));
                                return true;
                            }
                            else
                            if (recordtype.equals(IODriver.OPTIONS_TAG)) {
                                String a[] = (new String(buffer)).split("=");
                                if (a.length >= 2) {
                                    m_options.put(a[0],a[1]);
                                    Server.logger().fine(String.format("FileIODriver.Read() Option: %s=%s", a[0],a[1]));
                                }
                                return Read(startingtick,endingtick);
                            }
                            else
                            if (recordtype.equals(IODriver.HEADER_TAG)) {
                                header_buffer = m_header_buffer = buffer;
                            }
                            else
                            if (recordtype.equals(IODriver.SUBHEADER_TAG)) {
                                m_subheader = new SubHeader(ByteBuffer.wrap(buffer));
                            }
                            else
                            if (recordtype.equals(IODriver.SESSIONINFO_TAG)) {
                                sessioninfo_buffer = m_sessioninfo_buffer = buffer;
                            }
                            else
                            if (recordtype.equals(IODriver.VARHEADERS_TAG)) {
                                varheader_buffer = m_varheader_buffer = buffer;
                            }
                            else
                            if (recordtype.equals(IODriver.VARS_TAG)) {
                                var_buffer = m_var_buffer = buffer;

                                if (super.ProcessBuffers(
                                         ByteBuffer.wrap(header_buffer)
                                    ,    sessioninfo_buffer == null ? null : ByteBuffer.wrap(sessioninfo_buffer)
                                    ,    varheader_buffer   == null ? null : ByteBuffer.wrap(varheader_buffer)
                                    ,    ByteBuffer.wrap(var_buffer)
                                    ,    ByteBuffer.wrap(m_subheader._getByteArray())
                                    )
                                ) {
                                    getHeader().getSubHeader(m_subheader);  //register the subheader with the header

                                    //if between the ticks, get caught up if needed and return true.
                                    if (getHeader().getLatest_VarBufTick() >= startingtick && getHeader().getLatest_VarBufTick() <= endingtick) {

                                        if (!m_caughtup) {    //if we're not caught up, process all buffers
                                            m_caughtup = true;
                                            boolean ret = super.ProcessBuffers(
                                                         ByteBuffer.wrap(m_header_buffer)
                                                    ,    sessioninfo_buffer == null ? null : ByteBuffer.wrap(m_sessioninfo_buffer)
                                                    ,    varheader_buffer   == null ? null : ByteBuffer.wrap(m_varheader_buffer)
                                                    ,    ByteBuffer.wrap(m_var_buffer)
                                                    ,    ByteBuffer.wrap(m_subheader._getByteArray())
                                                );
                                            getHeader().getSubHeader(m_subheader);  //register the subheader with the header
                                            return ret;
                                        }
                                        return true;
                                    }
                                }
                            }
                        }
                    }

                    //if we've gone past the ending tick, then break the loop to return EOF
                    if (m_caughtup && getHeader().getLatest_VarBufTick() == 0 || getHeader().getLatest_VarBufTick() > endingtick) {
                        recordtype = IODriver.EOF_TAG;
                    }
                    else {
                        if (m_dis.read(recordtypebuffer) != recordtypebuffer.length) break;
                        recordtype = new String(recordtypebuffer);
                    }
                }
            }
        }
        catch (IOException e) {
        }

        isEOF(true);

        byte buffer[] = new byte[Header.SIZEOF_HEADER];
        super.ProcessBuffers(ByteBuffer.wrap(buffer), null, null, null, null);
        getHeader().getSubHeader(m_subheader);  //register the subheader with the header
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

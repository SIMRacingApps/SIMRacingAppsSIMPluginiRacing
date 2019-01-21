package com.SIMRacingApps.SIMPlugins.iRacing.SessionDataCache;

import com.SIMRacingApps.Data;
import com.SIMRacingApps.SIMPlugins.iRacing.iRacingSIMPlugin;

/**
 * @author Jeffrey Gilliam
 * @copyright Copyright (C) 2015 - 2019 Jeffrey Gilliam
 * @since 1.0
 * @license Apache License 2.0
 */
public abstract class SessionData extends Data {
    
    protected iRacingSIMPlugin m_SIMPlugin;
    private int m_sessionVersion = -1;
    private int m_sessionUniqueId = -1;
    
    public SessionData(iRacingSIMPlugin SIMPlugin, String name) {
        super(name);
        m_SIMPlugin = SIMPlugin;
    }

    public SessionData(iRacingSIMPlugin SIMPlugin, String name, Object value) {
        super(name,value);
        m_SIMPlugin = SIMPlugin;
    }
    
    public SessionData(iRacingSIMPlugin SIMPlugin, String name, Object value, String UOM) {
        super(name,value,UOM);
        m_SIMPlugin = SIMPlugin;
    }
    
    public SessionData(iRacingSIMPlugin SIMPlugin, String name, Object value, String UOM, String state) {
        super(name,value,UOM,state);
        m_SIMPlugin = SIMPlugin;
    }
    
    protected boolean _needsUpdating() {
        //if we're not connected, no update available
        if (!m_SIMPlugin.isConnected())
            return false;
        
        //See if the session data changed
        if (m_sessionUniqueId == m_SIMPlugin.getIODriver().getVars().getInteger("SessionUniqueID")
        &&  m_sessionVersion  == m_SIMPlugin.getIODriver().getHeader().getSessionInfoUpdate()
        )
            return false;
        
        m_sessionUniqueId = m_SIMPlugin.getIODriver().getVars().getInteger("SessionUniqueID");
        m_sessionVersion  = m_SIMPlugin.getIODriver().getHeader().getSessionInfoUpdate();
        return true;
    }
}

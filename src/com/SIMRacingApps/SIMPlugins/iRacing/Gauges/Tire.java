/**
 * 
 */
package com.SIMRacingApps.SIMPlugins.iRacing.Gauges;

import com.SIMRacingApps.Data;
import com.SIMRacingApps.Track;
import com.SIMRacingApps.SIMPlugins.iRacing.iRacingCar;
import com.SIMRacingApps.SIMPlugins.iRacing.iRacingGauge;
import com.SIMRacingApps.SIMPlugins.iRacing.IODrivers.IODriver;

/**
 * This is the base class for all tire reads.
 * Tire children should return the cold tire reading as the current value.
 * Tire children should return the value as soon as the tire is taken off the car for history value.
 * Tire children should always return the setup value for the next value.
 * 
 * @author Jeffrey Gilliam
 * @copyright Copyright (C) 2017 Jeffrey Gilliam
 * @since 1.4
 * @license Apache License 2.0
 */
public class Tire extends iRacingGauge {

    String m_tire;
    Data m_valueCurrent;
    Data m_valueNext;
    Data m_valueHistorical;
    
    public Tire(String type, iRacingCar car, Track track, IODriver IODriver,
            String varName, String defaultUOM, String tire) {
        super(type, car, track, IODriver, varName, defaultUOM, null);
        
        m_tire            = tire;
        m_valueCurrent    = new Data(m_varName,0.0,m_iRacingUOM);
        m_valueNext       = new Data(m_varName,0.0,m_iRacingUOM);
        m_valueHistorical = new Data(m_varName,0.0,m_iRacingUOM);
    }

    @Override 
    public Data getValueCurrent(String UOM) {
        Data d = super.getValueCurrent();
        d.setValue(m_valueCurrent.getDouble(),m_valueCurrent.getUOM(),m_valueCurrent.getState());
        return this._getReturnValue(d, UOM);
    }
    @Override 
    public Data getValueNext(String UOM) {
        Data d = super.getValueNext();
        d.setValue(m_valueNext.getDouble(),m_valueNext.getUOM(),m_valueNext.getState());
        return this._getReturnValue(d, UOM);
    }
    @Override 
    public Data getValueHistorical(String UOM) {
        Data d = super.getValueHistorical();
        d.setValue(m_valueHistorical.getDouble(),m_valueHistorical.getUOM(),m_valueHistorical.getState());
        return this._getReturnValue(d, UOM);
    }

    @Override
    public Data setChangeFlag(boolean flag) { 
        m_changeFlag = flag;
        return getChangeFlag();
    }
}

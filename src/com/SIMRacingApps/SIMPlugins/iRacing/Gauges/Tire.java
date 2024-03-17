/**
 * 
 */
package com.SIMRacingApps.SIMPlugins.iRacing.Gauges;

import com.SIMRacingApps.Data;
import com.SIMRacingApps.Server;
import com.SIMRacingApps.Track;
import com.SIMRacingApps.SIMPlugins.iRacing.iRacingCar;
import com.SIMRacingApps.SIMPlugins.iRacing.iRacingGauge;
import com.SIMRacingApps.SIMPlugins.iRacing.IODrivers.IODriver;

/**
 * This is the base class for all tire reads.
 * 
 * @author Jeffrey Gilliam
 * @copyright Copyright (C) 2015 - 2024 Jeffrey Gilliam
 * @since 1.5
 * @license Apache License 2.0
 */
public class Tire extends iRacingGauge {

    String m_tire;
    Data m_valueCurrent;
    Data m_valueNext;
    Data m_valueHistorical;
    int m_lapsHistorical;
    protected int m_usedCount = 1;
    
    public Tire(String type, iRacingCar car, Track track, IODriver IODriver,
            String varName, String defaultUOM, String tire) {
        super(type, car, track, IODriver, varName, defaultUOM, null, null);
        
        m_tire            = tire;
        m_valueCurrent    = new Data(m_varName,0.0,m_iRacingUOM,Data.State.NOTAVAILABLE);
        m_valueNext       = new Data(m_varName,0.0,m_iRacingUOM,Data.State.NOTAVAILABLE);
        m_valueHistorical = new Data(m_varName,0.0,m_iRacingUOM,Data.State.NOTAVAILABLE);
        m_lapsHistorical  = 0;
    }

    @Override 
    public Data getValueCurrent(String UOM) {
        Data d = super.getValueCurrent(UOM);
        d.setValue(m_valueCurrent.getDouble(),m_valueCurrent.getUOM(),m_valueCurrent.getState());
        return this._getReturnValue(d, UOM);
    }
    @Override 
    public Data getValueNext(String UOM) {
        Data d = super.getValueNext(UOM);
        d.setValue(m_valueNext.getDouble(),m_valueNext.getUOM(),m_valueNext.getState());
        return this._getReturnValue(d, UOM);
    }
    @Override 
    public Data getValueHistorical(String UOM) {
        Data d = super.getValueHistorical(UOM);
        d.setValue(m_valueHistorical.getDouble(),m_valueHistorical.getUOM(),m_valueHistorical.getState());
        return this._getReturnValue(d, UOM);
    }
    
    @Override
    public Data getLapsHistorical() {
        Data d = super.getLapsHistorical();
        d.setValue(m_lapsHistorical);
        return d;
    }
    
    @Override
    public Data getCount() {
        Data d = super.getCount();
        Data maxTires = this.m_car._getDataPublisherMaxTires();     //get the max tires from the plugin
        
        d.setValue(m_usedCount,"",Data.State.NORMAL);               //default to our counter
        
        if (maxTires == null) {                                     //if not publishing max tires from the plugin, try iRacing
            
            //as of June 2020, iRacing has tire counts. Use them instead of our own count
            //only can use them if max tires is defined for this session
            maxTires = getMaxCount();
            
            if (!maxTires.getState().equals(Data.State.NOTAVAILABLE) && Server.getArg("use-iRacing-tire-limit", true)) {
                Data count = this._readVar(m_tire + "TiresAvailable","");   //has the remaining tires, calculate used
                
                if (!count.getState().equals(Data.State.NOTAVAILABLE) && !maxTires.getState().equals(Data.State.NOTAVAILABLE)) {
                    d.setValue(maxTires.getInteger() - count.getInteger(),"",Data.State.NORMAL);
                }
            }
        }
        return d;
    }
    
    @Override
    public Data getMaxCount() {
        Data d = super.getMaxCount();
        String session = this._readVar("SessionNum","").getStringFormatted("%.0f");
        String sessionType = this.m_IODriver.getSessionInfo().getString("SessionInfo","Sessions",session,"SessionType").toUpperCase();
        
        if (sessionType.equals("RACE")) {
            Data maxTires = this.m_car._getDataPublisherMaxTires();    //get the default max tires from the plugin

            if (maxTires != null) {  //if plugin not active, use iRacing's values
                d.setValue(maxTires.getValue(),"",maxTires.getState());
            }
            else {
                //as of June 2020, iRacing has max tire counts per tire
                if (Server.getArg("use-iRacing-tire-limit", true)) {
                    Data count = this._readVar("PlayerCarDryTireSetLimit","");
                    
                    //greater than zero means limit is in place for this session
                    if (!count.getState().equals(Data.State.NOTAVAILABLE) && count.getInteger() > 0) {
                        d.setValue(count.getInteger(),"",Data.State.NORMAL);
                    }
                }
            }
        }
        
        return d;
    }
}

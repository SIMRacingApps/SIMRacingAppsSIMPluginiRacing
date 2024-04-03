/**
 * 
 */
package com.SIMRacingApps.SIMPlugins.iRacing.Gauges;

import com.SIMRacingApps.Data;
import com.SIMRacingApps.Server;
import com.SIMRacingApps.Track;
import com.SIMRacingApps.SIMPlugins.iRacing.iRacingCar;
import com.SIMRacingApps.SIMPlugins.iRacing.IODrivers.IODriver;

/**
 * @author Jeffrey Gilliam
 * @copyright Copyright (C) 2015 - 2024 Jeffrey Gilliam
 * @since 1.15
 * @license Apache License 2.0
 */
public class TireCompound extends Tire {
    
    public TireCompound(String type, iRacingCar car, Track track,
            IODriver IODriver) {
        super(type, car, track, IODriver, "CarIdxTireCompound", "", "");
        
        m_valueCurrent    = new Data(m_varName,-1.0,"",Data.State.NORMAL);
        m_valueNext       = new Data(m_varName,-1.0,"",Data.State.NORMAL);
        m_valueHistorical = new Data(m_varName,-1.0,"",Data.State.NORMAL);
    }
    
    @Override 
    public Data setValueNext(double d,String UOM) {
        Data r = super.setValueNext(d, "");
        
        //we must set the value again because the super just returns the current value.
        r.setValue(d,"");
        
        if (r.compare(m_valueNext) != 0) {
            _setSIMCommandTimestamp(true,r.getDouble());
            m_valueNext = r;
            Server.logger().info(String.format("TireCompound: setting to %s",m_valueNext.getString()));
        }
        
        this.m_valueCurrent = _readVar();
        this.m_isDirty = this.m_valueCurrent.getDouble() != this.m_valueNext.getDouble();
        
        return this._getReturnValue(m_valueNext, "");   
    }

    public void _tireCurrent(Tire tire) {
        this.m_valueCurrent = _readVar();
        //if command is not pending, update it from the SIM
        if (this._getSIMCommandTimestamp() == 0) {
            if (this.m_car.isME())
                this.m_valueNext    = _readVar("PitSvTireCompound","");
            else
                this.m_valueNext    = this.m_valueCurrent;  //do this until iRacing provides it in the telemetry for the other cars
        }
        this.m_isDirty = this.m_valueCurrent.getDouble() != this.m_valueNext.getDouble();
    }

    public void _tireChanged(Tire tire) {
        this.m_lapChanged      = tire.getLapChanged().getInteger();
        this.m_usedCount       = tire.getCount().getInteger();
        this.m_valueHistorical = m_valueCurrent;
        this.m_lapsHistorical  = tire.getLapsHistorical().getInteger(); 
    }
}

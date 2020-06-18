/**
 * 
 */
package com.SIMRacingApps.SIMPlugins.iRacing.Gauges;

import java.util.Map;

import com.SIMRacingApps.Car;
import com.SIMRacingApps.Data;
import com.SIMRacingApps.Server;
import com.SIMRacingApps.Track;
import com.SIMRacingApps.SIMPlugins.iRacing.iRacingCar;
import com.SIMRacingApps.SIMPlugins.iRacing.iRacingGauge;
import com.SIMRacingApps.SIMPlugins.iRacing.IODrivers.IODriver;
import com.SIMRacingApps.Util.State;

/**
 * This class is responsible for managing the changes the SIM can make, but it doesn't tell us it
 * changed them in the pit flags. But, it does tell us what the next value will be, but not the current value.
 * For current value, we will have to save the next value when we pit.
 * 
 * @author Jeffrey Gilliam
 * @copyright Copyright (C) 2017 Jeffrey Gilliam
 * @since 1.5
 * @license Apache License 2.0
 */
public class Changeables extends iRacingGauge {
    
    Data m_valueCurrent;
    Data m_valueNext;
    Data m_valueHistorical;
    int m_lapsHistorical;
    int m_usedCount = 1;

    public Changeables(String type, iRacingCar car, Track track,
            IODriver IODriver, String varName, String defaultUOM, 
            Map<String, Map<String, Map<String, Object>>> simGaugesBefore, 
            Map<String, Map<String, Map<String, Object>>> simGaugesAfter) {
        super(type, car, track, IODriver, varName, defaultUOM, simGaugesBefore, simGaugesAfter);
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
        d.setValue(m_lapsHistorical,"lap",Data.State.NORMAL);
        return d;
    }
    
    @Override
    public void _onDataVersionChange(State status, int currentLap,double sessionTime,double lapCompletedPercent,double trackLength) {
        super._onDataVersionChange(status, currentLap, sessionTime, lapCompletedPercent, trackLength);
        
        //can only read the pit values for ME and only when not in the garage
        //do not process if there are pending changes to be sent to the SIM
        if (m_car.isME() 
        && !m_car.getStatus().equals(Car.Status.INGARAGE)
//        && !((iRacingCar)m_car)._setupCommandsPending()
        && this._getSIMCommandTimestamp() == 0.0 //there's no pending command for this gauge
        ) {
            Data    varValue   = _readVar();
            
            //if current is not set, then initialize it with the first value we see
            //otherwise, it should not change until the tire is changed
            //TODO: check if dropped in from Garage, if changes made in Garage are picked up.
            //      could be dropped on pit lane, or on track when qualifying or racing.
            if ((m_valueCurrent.getState().equals(Data.State.OFF) || m_valueCurrent.getState().equals(Data.State.NOTAVAILABLE)) 
            && !varValue.getState().equals(Data.State.OFF)
            && !varValue.getState().equals(Data.State.NOTAVAILABLE)
            ) {
                m_valueCurrent.set(varValue);
                Server.logger().info(String.format(
                        "%s: New Car, initializing valueCurrent = %f %s, %f %s",
                        m_type,
                        m_valueCurrent.getDouble(),
                        m_valueCurrent.getUOM(),
                        m_valueCurrent.convertUOM(this.m_measurementSystem).getDouble(),
                        m_valueCurrent.convertUOM(this.m_measurementSystem).getUOM()
                 ));
            }
            
            if (!varValue.getState().equalsIgnoreCase(Data.State.NOTAVAILABLE)
            &&  !varValue.getState().equalsIgnoreCase(Data.State.OFF)
            ) {
	            //Detect we pitted. Assume all tracked PitSvFlags are off and we cannot use them
	            //So, best I can do is assume of you in there long enough, it applied the changes
	            boolean pitted = m_car.getPitTime().getDouble() >= Server.getArg("pit-service-min", 1.0)
	                          && m_car.getLap(Car.LapType.PITTED).getInteger() == currentLap;  
	            
	            //If the gauge was changed because we pitted and we had a pending change queued up
	            if (m_changeFlag 
	            && pitted
                && currentLap > 0
	            && (   status.getState().equals(Car.Status.INPITSTALL)
	                || status.getState().equals(Car.Status.EXITINGPITSTALL)
	               )
	            ) {
	                //don't record anything if you haven't run a lap
	                if (m_lapChanged != currentLap) {
	                    Server.logger().info(String.format(
	                            "%s: Change applied %f %s, %f %s, saving valueCurrent = %f %s, %f %s",
	                            m_type,
	                            varValue.getDouble(),
	                            varValue.getUOM(),
	                            varValue.convertUOM(this.m_measurementSystem).getDouble(),
	                            varValue.convertUOM(this.m_measurementSystem).getUOM(),
	                            m_valueCurrent.getDouble(),
	                            m_valueCurrent.getUOM(),
	                            m_valueCurrent.convertUOM(this.m_measurementSystem).getDouble(),
	                            m_valueCurrent.convertUOM(this.m_measurementSystem).getUOM()
	                    ));
	                    
	                    m_valueHistorical = new Data(m_valueCurrent);   //save the previous current value as historical
	                    m_lapsHistorical  = this.getLaps(currentLap).getInteger();
	                    m_lapChanged      = currentLap;                 //save the lap
	                    m_usedCount++;                                  //count the uses
	                }
	                
	                m_valueCurrent    = new Data(varValue);             //save the current tire pressure
	            }
            }	
            
            //read the var and set the Next Value to it for the clients to display
            m_valueNext.set(varValue);
            
            //if the next value is different from the current value
            //mark it dirty to be applied to next pit stop
            this._setIsDirty(this._roundToIncrement(m_valueCurrent.convertUOM(m_UOM).getDouble(),m_UOM) != this._roundToIncrement(m_valueNext.convertUOM(m_UOM).getDouble(),m_UOM));
            
            //assume the change flag matches the dirty flag
            m_changeFlag = this.getIsDirty().getBoolean();            
        }
        else {
            //TODO: Do I retrofit the old logic here somehow just to support my recorded files?
            //      I don't think any of the users have recorded a file.
        }
    }

    @Override
    public Data getCount() {
        Data d = super.getCount();
        d.setValue(m_usedCount,"",Data.State.NORMAL);
        return d;
    }
}

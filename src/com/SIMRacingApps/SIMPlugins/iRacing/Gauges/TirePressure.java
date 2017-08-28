/**
 * 
 */
package com.SIMRacingApps.SIMPlugins.iRacing.Gauges;

import com.SIMRacingApps.Data;
import com.SIMRacingApps.Track;
import com.SIMRacingApps.SIMPlugins.iRacing.PitSvFlags;
import com.SIMRacingApps.SIMPlugins.iRacing.iRacingCar;
import com.SIMRacingApps.SIMPlugins.iRacing.IODrivers.IODriver;

/**
 * @author Jeffrey Gilliam
 * @copyright Copyright (C) 2017 Jeffrey Gilliam
 * @since 1.4
 * @license Apache License 2.0
 */
public class TirePressure extends Tire {

    private int m_prevPitFlags;
    private Data m_varValue;
    
    public TirePressure(String type, iRacingCar car, Track track,
            IODriver IODriver, String tire) {
        super(type, car, track, IODriver, tire + "coldPressure", "kPa", tire);
        
        m_prevPitFlags = 0;
        m_varValue     = _readVar();
        m_valueCurrent = new Data(m_varValue);
    }

    @Override 
    public Data setValueNext(double d,String UOM) {
        if (!m_isFixed) {
            m_valueNext = new Data("",d,UOM).convertUOM(m_iRacingUOM);
            if (m_valueNext.getDouble() < m_capacityMinimum.convertUOM(m_iRacingUOM).getDouble())
                m_valueNext = m_capacityMinimum.convertUOM(m_iRacingUOM);
            
            if (m_valueNext.getDouble() > m_capacityMaximum.convertUOM(m_iRacingUOM).getDouble())
                m_valueNext = m_capacityMaximum.convertUOM(m_iRacingUOM);
        }
        
        m_changeFlag = true;
        
        return getValueNext(UOM);
    }
    
    @Override 
    public Data decrementValueNext(String UOM) {
        if (!m_isFixed) {
            m_valueNext.setValue(getValueNext(m_iRacingUOM).getDouble() - getCapacityIncrement(m_iRacingUOM).getDouble());
            if (m_valueNext.getDouble() < m_capacityMinimum.convertUOM(m_iRacingUOM).getDouble())
                m_valueNext = m_capacityMinimum.convertUOM(m_iRacingUOM);
            
            if (m_valueNext.getDouble() > m_capacityMaximum.convertUOM(m_iRacingUOM).getDouble())
                m_valueNext = m_capacityMaximum.convertUOM(m_iRacingUOM);
        }
        
        m_changeFlag = true;
        
        return getValueNext(UOM);
    }
    
    @Override 
    public Data incrementValueNext(String UOM) {
        if (!m_isFixed) {
            m_valueNext.setValue(getValueNext(m_iRacingUOM).getDouble() + getCapacityIncrement(m_iRacingUOM).getDouble());
            if (m_valueNext.getDouble() < m_capacityMinimum.convertUOM(m_iRacingUOM).getDouble())
                m_valueNext = m_capacityMinimum.convertUOM(m_iRacingUOM);
            
            if (m_valueNext.getDouble() > m_capacityMaximum.convertUOM(m_iRacingUOM).getDouble())
                m_valueNext = m_capacityMaximum.convertUOM(m_iRacingUOM);
            
        }
        
        m_changeFlag = true;
        
        return getValueNext(UOM);
    }
    
    @Override
    public void onDataVersionChange(int currentLap,double sessionTime,double lapCompletedPercent,double trackLength) {
        super.onDataVersionChange(currentLap, sessionTime, lapCompletedPercent, trackLength);
        
        //can only read the pit values for ME
        if (m_car.isME()) {
            //check if the pit flags exist just in case a recorded file without them is played
            if (m_IODriver.getVarHeaders().getVarHeader("PitSvFlags") != null) {
                int pitFlags = m_IODriver.getVars().getBitfield("PitSvFlags");

                //before we read the var value, see if the tire was changed
                //then save off the value that got applied
                //but don't save it for the same stop, not any value in doing that
                if ((m_prevPitFlags & PitSvFlags.getFlag(m_tire)) != 0 
                &&  (pitFlags & PitSvFlags.getFlag(m_tire)) == 0
                &&  m_lapChanged != currentLap
                ) {
                    m_valueHistorical = new Data(m_varValue);       //save the previous value as historical
                    m_valueNext       = new Data(m_varValue);       //save the previous value as the next value
                    m_valueCurrent    = new Data(m_varValue);       //only update this upon change of tire
                    m_changeFlag      = false;                      //uncheck the change flag
                    m_lapChanged      = currentLap;
                    m_usedCount++;
                }

                m_varValue = _readVar();    //read the current value

                //if the next value is different from the current value
                //mark it dirty to be applied to next pit stop
                if (m_valueCurrent.compare(m_valueNext) != 0) {
                    this._setIsDirty(true);            
                }
                else {
                    this._setIsDirty(false);
                }
                
                //now update the SIM flag so the commands can be sent.
                if (m_changeFlag != ((pitFlags & PitSvFlags.getFlag(m_tire)) != 0)
                ||  m_valueNext.compare(m_varValue) != 0
                ) {
                    m_updateSIM = true;
                }
                else {
                    m_updateSIM = false;
                }
                
                m_prevPitFlags = pitFlags;                          //save the pit flags
            }
            else {
                //TODO: Do I retrofit the old logic here somehow just to support my recorded files?
                //      I don't think any of the users have recorded a file.
            }
        }
    }
}

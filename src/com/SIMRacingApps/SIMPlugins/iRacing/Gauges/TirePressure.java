/**
 * 
 */
package com.SIMRacingApps.SIMPlugins.iRacing.Gauges;

import com.SIMRacingApps.Car;
import com.SIMRacingApps.Data;
import com.SIMRacingApps.Server;
import com.SIMRacingApps.Track;
import com.SIMRacingApps.SIMPlugins.iRacing.PitSvFlags;
import com.SIMRacingApps.SIMPlugins.iRacing.iRacingCar;
import com.SIMRacingApps.SIMPlugins.iRacing.IODrivers.IODriver;
import com.SIMRacingApps.Util.State;

/**
 * This class is responsible for managing the tire pressures.
 * 
 * For the current value, it should return the pressure of the tire at the moment it was put on the car.
 * Most, if not all race cars, do not have real-time pressure monitors. 
 * iRacing doesn't output them, even if they do in real life.
 * 
 * For the next value, return what ever the user wants the pressure to be changed to on the next pit stop.
 * I will return what iRacing says, so it may be a delayed, rounded, or limited from what the user wants.
 * 
 * For the historical value, simply save off the current value of the tire that came off the car. 
 * iRacing doesn't return the actual pressure that it built up to.
 * 
 * @author Jeffrey Gilliam
 * @copyright Copyright (C) 2017 Jeffrey Gilliam
 * @since 1.4
 * @license Apache License 2.0
 */
public class TirePressure extends Tire {

    public TirePressure(String type, iRacingCar car, Track track,
            IODriver IODriver, String tire) {
        super(type, car, track, IODriver, tire + "coldPressure", "kPa", tire);
    }

    @Override
    public Data setChangeFlag(boolean flag) {
        if (m_changeFlag != flag) {
            m_changeFlag = flag;
            m_updateSIM = true;
        }
        return getChangeFlag();
    }
    
    @Override 
    public Data setValueNext(double d,String UOM) {
        Data r = super.setValueNext(d, UOM);
        
        if (!m_isFixed) {
            //convert the requested value to the iRacing UOM and round it up to the next increment
            //while keeping it within the min/max boundaries
            r.setValue(this._roundUpToIncrement(
                    r.convertUOM(m_iRacingUOM).getDouble(),m_iRacingUOM
                    ),m_iRacingUOM,Data.State.NORMAL);
            if (r.compare(m_valueNext) != 0) {
                m_valueNext.set(r);
                m_updateSIM = true;
            }
        }

        setChangeFlag(true);
        
        return this._getReturnValue(r, UOM);   
    }
    
    @Override 
    public Data decrementValueNext(String UOM) {
        Data r = super.decrementValueNext(m_iRacingUOM);
        
        if (!m_isFixed) {
            r.setValue(this._roundUpToIncrement(
                    r.getDouble() - this.m_capacityIncrement.convertUOM(m_iRacingUOM).getDouble(), 
                    m_iRacingUOM
                    ),m_iRacingUOM,Data.State.NORMAL);
            if (r.compare(m_valueNext) != 0) {
                m_valueNext.set(r);
                m_updateSIM = true;
            }
        }
        
        setChangeFlag(true);
        
        return this._getReturnValue(r, UOM);   
    }
    
    @Override 
    public Data incrementValueNext(String UOM) {
        Data r = super.incrementValueNext(m_iRacingUOM);
        
        if (!m_isFixed) {
            r.setValue(this._roundUpToIncrement(
                    r.getDouble() + this.m_capacityIncrement.convertUOM(m_iRacingUOM).getDouble(),
                    m_iRacingUOM
                    ),m_iRacingUOM,Data.State.NORMAL);
            if (r.compare(m_valueNext) != 0) {
                m_valueNext.set(r);
                m_updateSIM = true;
            }
        }
        
        setChangeFlag(true);
        
        return this._getReturnValue(r, UOM);   
    }
    
    @Override
    public void onDataVersionChange(State status, int currentLap,double sessionTime,double lapCompletedPercent,double trackLength) {
        super.onDataVersionChange(status, currentLap, sessionTime, lapCompletedPercent, trackLength);
        
        //can only read the pit values for ME and only when not in the garage
        //do not process if there are pending changes to be sent to the SIM
        if (m_car.isME() && !m_car.getStatus().equals(Car.Status.INGARAGE) && !m_updateSIM) {
            
            //check if the pit flags exist just in case a recorded file without them is played
            if (m_IODriver.getVarHeaders().getVarHeader("PitSvFlags") != null) {

                //get the current var values
                boolean changeFlag = ((m_IODriver.getVars().getBitfield("PitSvFlags") & PitSvFlags.getFlag(m_tire)) != 0);
                Data    varValue   = _readVar();
                
                //if current is not set, then initialize it with the first value we see
                //otherwise, it should not change until the tire is changed
                //TODO: check if dropped in from Garage, if changes made in Garage are picked up.
                //      could be dropped on pit lane, or on track when qualifying or racing.
                if ((m_valueCurrent.getDouble() == 0.0 && !varValue.getState().equals(Data.State.OFF))
                ) {
                    m_valueCurrent.set(varValue);
                    Server.logger().info(String.format("TirePressure%s: New Car, initializing valueCurrent = %f %s, %f psi",m_tire,m_valueCurrent.getDouble(),m_valueCurrent.getUOM(),m_valueCurrent.convertUOM("psi").getDouble()));
                }

                //before we read the var value, see if the tire was changed
                //then save off the value that was on the car
                //TODO: How to tell the difference between the user unchecking it and the tire being changed?
                if (m_changeFlag                                                //it was true
                && !changeFlag                                                  //but now is false
                && (   status.getState().equals(Car.Status.ENTERINGPITSTALL)    //we're in the pit stall 
                    || status.getState().equals(Car.Status.INPITSTALL)
                   )
//                || status.getPrevState().equals(Car.Status.INVALID)             //reset 
                ) {
                    Server.logger().info(String.format("TirePressure%s: Change detected, saving valueCurrent = %f %s, %f psi",m_tire,m_valueCurrent.getDouble(),m_valueCurrent.getUOM(),m_valueCurrent.convertUOM("psi").getDouble()));
                    //don't record anything if you haven't run a lap
                    if (m_lapChanged != currentLap) {
                        m_valueHistorical = new Data(m_valueCurrent);   //save the previous current value as historical
                        m_lapChanged      = currentLap;                 //save the lap
                        m_usedCount++;                                  //count the uses
                    }
                    
                    m_valueCurrent    = new Data(varValue);             //save the current tire pressure
                }

                //read the var and set the Next Value to it for the clients to display
                m_valueNext.set(varValue);
                
                //if the next value is different from the current value
                //mark it dirty to be applied to next pit stop
                this._setIsDirty(m_valueCurrent.compare(m_valueNext) != 0);            

                m_changeFlag = changeFlag;
            }
            else {
                //TODO: Do I retrofit the old logic here somehow just to support my recorded files?
                //      I don't think any of the users have recorded a file.
            }
        }
    }
}

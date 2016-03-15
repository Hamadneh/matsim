/* *********************************************************************** *
 * project: org.matsim.*
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2013 by the members listed in the COPYING,        *
 *                   LICENSE and WARRANTY file.                            *
 * email           : info at matsim dot org                                *
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 *   This program is free software; you can redistribute it and/or modify  *
 *   it under the terms of the GNU General Public License as published by  *
 *   the Free Software Foundation; either version 2 of the License, or     *
 *   (at your option) any later version.                                   *
 *   See also COPYING, LICENSE and WARRANTY file                           *
 *                                                                         *
 * *********************************************************************** */

package playground.michalm.taxi.optimizer.rules;

import java.util.*;

import org.matsim.api.core.v01.network.*;
import org.matsim.contrib.dvrp.data.*;
import org.matsim.contrib.dvrp.schedule.*;
import org.matsim.contrib.dvrp.schedule.Schedule.ScheduleStatus;

import playground.michalm.taxi.data.TaxiRequest;
import playground.michalm.taxi.optimizer.*;
import playground.michalm.taxi.schedule.*;
import playground.michalm.taxi.schedule.TaxiTask.TaxiTaskType;
import playground.michalm.zone.util.*;
import playground.michalm.zone.util.SquareGridSystem.SquareZone;


public class RuleBasedTaxiOptimizer
    extends AbstractTaxiOptimizer
{
    private static final double CELL_SIZE = 1000;//in [m]//TODO
    private static ZonalSystem<SquareZone> zonalSystem;//TODO

    protected final BestDispatchFinder dispatchFinder;

    private IdleTaxiZonalRegistry idleTaxiRegistry;
    private UnplannedRequestZonalRegistry unplannedRequestRegistry;

    public RuleBasedTaxiOptimizer(TaxiOptimizerConfiguration optimConfig)
    {
        super(optimConfig, new TreeSet<TaxiRequest>(Requests.ABSOLUTE_COMPARATOR), false);

        if (optimConfig.scheduler.getParams().vehicleDiversion) {
            throw new RuntimeException("Diversion is not supported by RuleBasedTaxiOptimizer");
        }

        dispatchFinder = new BestDispatchFinder(optimConfig);

        //TODO temp solution
        if (zonalSystem == null) {
            Network network = optimConfig.context.getScenario().getNetwork();
            zonalSystem = new SquareGridSystem(network, CELL_SIZE);
        }
        
        idleTaxiRegistry = new IdleTaxiZonalRegistry(zonalSystem, optimConfig.scheduler);
        unplannedRequestRegistry = new UnplannedRequestZonalRegistry(zonalSystem);
    }


    @Override
    protected void scheduleUnplannedRequests()
    {
        if (isReduceTP()) {
            scheduleIdleVehiclesImpl();//reduce T_P to increase throughput (demand > supply)
        }
        else {
            scheduleUnplannedRequestsImpl();//reduce T_W (regular NOS)
        }
    }


    private boolean isReduceTP()
    {
        switch (optimConfig.goal) {
            case MIN_PICKUP_TIME:
                return true;

            case MIN_WAIT_TIME:
                return false;

            case DEMAND_SUPPLY_EQUIL:
                int awaitingReqCount = Requests.countRequests(unplannedRequests,
                        new Requests.IsUrgentPredicate(optimConfig.context.getTime()));

                return awaitingReqCount > idleTaxiRegistry.getVehicleCount();

            default:
                throw new IllegalStateException();
        }
    }

    //request-initiated scheduling
    private void scheduleUnplannedRequestsImpl()
    {
        int idleCount = idleTaxiRegistry.getVehicleCount();

        Iterator<TaxiRequest> reqIter = unplannedRequests.iterator();
        while (reqIter.hasNext() && idleCount > 0) {
            TaxiRequest req = reqIter.next();

            Iterable<Vehicle> selectedVehs = idleCount > optimConfig.nearestVehiclesLimit ? // we do not want to visit more than a quarter of zones
                    idleTaxiRegistry.findNearestVehicles(req.getFromLink().getFromNode(), optimConfig.nearestVehiclesLimit) : //
                    idleTaxiRegistry.getVehicles();

            BestDispatchFinder.Dispatch best = dispatchFinder.findBestVehicleForRequest(req,
                    selectedVehs);

            optimConfig.scheduler.scheduleRequest(best.vehicle, best.request, best.path);
            
            reqIter.remove();
            unplannedRequestRegistry.removeRequest(req);
            idleCount--;
        }
    }

    
    //vehicle-initiated scheduling
    private void scheduleIdleVehiclesImpl()
    {
        Iterator<Vehicle> vehIter = idleTaxiRegistry.getVehicles().iterator();
        while (vehIter.hasNext() && !unplannedRequests.isEmpty()) {
            Vehicle veh = vehIter.next();

            Link link = ((TaxiStayTask)veh.getSchedule().getCurrentTask()).getLink();
            Iterable<TaxiRequest> selectedReqs = unplannedRequests.size() > optimConfig.nearestRequestsLimit ? //
                    unplannedRequestRegistry.findNearestRequests(link.getToNode(), optimConfig.nearestRequestsLimit) : //
                    unplannedRequests;

            BestDispatchFinder.Dispatch best = dispatchFinder.findBestRequestForVehicle(veh,
                    selectedReqs);

            optimConfig.scheduler.scheduleRequest(best.vehicle, best.request, best.path);

            unplannedRequests.remove(best.request);
            unplannedRequestRegistry.removeRequest(best.request);
        }
    }


    @Override
    public void requestSubmitted(Request request)
    {
        super.requestSubmitted(request);
        unplannedRequestRegistry.addRequest((TaxiRequest)request);
    }


    @Override
    public void nextTask(Schedule<? extends Task> schedule)
    {
        super.nextTask(schedule);

        if (schedule.getStatus() == ScheduleStatus.COMPLETED) {
            TaxiStayTask lastTask = (TaxiStayTask)Schedules.getLastTask(schedule);
            if (lastTask.getBeginTime() < schedule.getVehicle().getT1()) {
                idleTaxiRegistry.removeVehicle(schedule.getVehicle());
            }
        }
        else if (optimConfig.scheduler.isIdle(schedule.getVehicle())) {
            idleTaxiRegistry.addVehicle(schedule.getVehicle());
        }
        else {
            if (!Schedules.isFirstTask(schedule.getCurrentTask())) {
                TaxiTask previousTask = (TaxiTask)Schedules.getPreviousTask(schedule);
                if (previousTask.getTaxiTaskType() == TaxiTaskType.STAY) {
                    idleTaxiRegistry.removeVehicle(schedule.getVehicle());
                }
            }
        }
    }


    @Override
    protected boolean doReoptimizeAfterNextTask(TaxiTask newCurrentTask)
    {
        return newCurrentTask.getTaxiTaskType() == TaxiTaskType.STAY;
    }
}
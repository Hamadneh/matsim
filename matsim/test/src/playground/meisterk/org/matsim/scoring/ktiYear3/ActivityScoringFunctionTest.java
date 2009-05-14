/* *********************************************************************** *
 * project: org.matsim.*
 * ActivityScoringFunctionTest.java
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2008 by the members listed in the COPYING,        *
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

package playground.meisterk.org.matsim.scoring.ktiYear3;

import java.util.TreeMap;

import org.apache.log4j.Logger;
import org.matsim.api.basic.v01.Id;
import org.matsim.api.basic.v01.TransportMode;
import org.matsim.core.api.facilities.ActivityFacilities;
import org.matsim.core.api.facilities.ActivityFacility;
import org.matsim.core.api.facilities.ActivityOption;
import org.matsim.core.api.network.Link;
import org.matsim.core.api.network.Node;
import org.matsim.core.api.population.Activity;
import org.matsim.core.api.population.Leg;
import org.matsim.core.api.population.NetworkRoute;
import org.matsim.core.api.population.Person;
import org.matsim.core.api.population.Plan;
import org.matsim.core.basic.v01.IdImpl;
import org.matsim.core.basic.v01.facilities.BasicOpeningTime.DayType;
import org.matsim.core.config.Config;
import org.matsim.core.config.groups.CharyparNagelScoringConfigGroup;
import org.matsim.core.facilities.ActivityFacilitiesImpl;
import org.matsim.core.facilities.OpeningTimeImpl;
import org.matsim.core.network.NetworkLayer;
import org.matsim.core.population.PersonImpl;
import org.matsim.core.scoring.ScoringFunction;
import org.matsim.core.utils.geometry.CoordImpl;
import org.matsim.core.utils.misc.Time;
import org.matsim.locationchoice.facilityload.FacilityPenalty;
import org.matsim.population.Desires;
import org.matsim.testcases.MatsimTestCase;

public class ActivityScoringFunctionTest extends MatsimTestCase {

	private Person person;
	private Plan plan;
	private Config config;
	private ActivityFacilities facilities;
	private NetworkLayer network;
	
	/*package*/ static final Logger logger = Logger.getLogger(ActivityScoringFunctionTest.class);

	protected void setUp() throws Exception {
		super.setUp();
		
		// generate config
		this.config = super.loadConfig(null);
		CharyparNagelScoringConfigGroup scoring = this.config.charyparNagelScoring();
		scoring.setBrainExpBeta(2.0);
		scoring.setLateArrival(0.0);
		scoring.setEarlyDeparture(-6.0);
		scoring.setPerforming(+6.0);
		scoring.setTraveling(0.0);
		scoring.setTravelingPt(0.0);
		scoring.setMarginalUtlOfDistanceCar(0.0);
		scoring.setWaiting(0.0);
		
		// generate person
		this.person = new PersonImpl(new IdImpl("123"));

		// generate facilities
		this.facilities = new ActivityFacilitiesImpl();
		ActivityFacility facility1 = this.facilities.createFacility(new IdImpl("1"), new CoordImpl(0.0, 0.0));
		ActivityFacility facility3 = this.facilities.createFacility(new IdImpl("3"), new CoordImpl(1000.0, 1000.0));
		ActivityFacility facility5 = this.facilities.createFacility(new IdImpl("5"), new CoordImpl(1000.0, 1010.0));
		
		// generate network
		this.network = new NetworkLayer();
		Node node1 = network.createNode(new IdImpl("1"), new CoordImpl(    0.0, 0.0));
		Node node2 = network.createNode(new IdImpl("2"), new CoordImpl(  500.0, 0.0));
		Node node3 = network.createNode(new IdImpl("3"), new CoordImpl( 5500.0, 0.0));
		Node node4 = network.createNode(new IdImpl("4"), new CoordImpl( 6000.0, 0.0));
		Node node5 = network.createNode(new IdImpl("5"), new CoordImpl(11000.0, 0.0));
		Node node6 = network.createNode(new IdImpl("6"), new CoordImpl(11500.0, 0.0));
		Link link1 = network.createLink(new IdImpl("1"), node1, node2, 500, 25, 3600, 1);
		network.createLink(new IdImpl("2"), node2, node3, 5000, 50, 3600, 1);
		Link link3 = network.createLink(new IdImpl("3"), node3, node4, 500, 25, 3600, 1);
		network.createLink(new IdImpl("4"), node4, node5, 5000, 50, 3600, 1);
		Link link5 = network.createLink(new IdImpl(5), node5, node6, 500, 25, 3600, 1);

		// generate desires
		Desires desires = person.createDesires("test desires");
		desires.putActivityDuration("home", Time.parseTime("16:00:00"));
		desires.putActivityDuration("work_sector3", Time.parseTime("07:00:00"));
		desires.putActivityDuration("leisure", Time.parseTime("01:00:00"));
		
		// generate plan
		plan = person.createPlan(true);

		Activity act = plan.createActivity("home", facility1);
		act.setLink(link1);
		Leg leg = this.plan.createLeg(TransportMode.car);
		NetworkRoute route = (NetworkRoute) network.getFactory().createRoute(TransportMode.car, link1, link3);
		leg.setRoute(route);
		route.setDistance(25000.0);
		route.setTravelTime(Time.parseTime("00:30:00"));

		act = plan.createActivity("work_sector3", facility3);
		act.setLink(link3);
		leg = this.plan.createLeg(TransportMode.pt);
		route = (NetworkRoute) network.getFactory().createRoute(TransportMode.car, link3, link5);
		leg.setRoute(route);
		route.setDistance(2000.0);
		route.setTravelTime(Time.parseTime("00:05:00"));

		act = plan.createActivity("leisure", facility5);
		act.setLink(link5);
		leg = this.plan.createLeg(TransportMode.pt);
		route = (NetworkRoute) network.getFactory().createRoute(TransportMode.car, link5, link3);
		leg.setRoute(route);
		route.setDistance(2000.0);
		route.setTravelTime(Time.parseTime("00:05:00"));

		act = plan.createActivity("work_sector3", facility3);
		act.setLink(link3);
		leg = this.plan.createLeg(TransportMode.car);
		route = (NetworkRoute) network.getFactory().createRoute(TransportMode.car, link3, link1);
		leg.setRoute(route);
		route.setDistance(25000.0);
		route.setTravelTime(Time.parseTime("00:30:00"));
		
		act = plan.createActivity("home", facility1);
		act.setLink(link1);

	}

	public void testAlwaysOpen() {
		
		ActivityFacility facility = this.facilities.getFacilities().get(new IdImpl("1"));
		ActivityOption actOpt = facility.createActivityOption("home");
		actOpt.setCapacity(1.0);

		// []{end home, work_sector3, leisure, work_Sector3, start home, finish, reset}
		String[] expectedTooShortDurationsSequence = new String[]{"00:00:00", "00:00:00", "00:10:00",  "00:10:00", "00:10:00", "00:10:00", "00:00:00"};
		String[] expectedWaitingTimeSequence = new String[]{"00:00:00", "00:00:00", "00:00:00", "00:00:00", "00:00:00", "00:00:00", "00:00:00"};
		TreeMap<String, String[]> expectedAccumulatedActivityDurations = new TreeMap<String, String[]>();
		expectedAccumulatedActivityDurations.put("home", new String[]{null, null, null, null, "14:30:00", "14:30:00", null});
		expectedAccumulatedActivityDurations.put("work_sector3", new String[]{null, "06:00:00", "06:00:00", "08:00:00", "08:00:00", "08:00:00", null});
		expectedAccumulatedActivityDurations.put("leisure", new String[]{null, null, "00:20:00", "00:20:00", "00:20:00", "00:20:00", null});
		String[] expectedNegativeDurationsSequence = new String[]{"00:00:00", "00:00:00", "00:00:00", "00:00:00", "00:00:00", "00:00:00", "00:00:00"};
		this.runTest(
				expectedTooShortDurationsSequence, 
				expectedWaitingTimeSequence, 
				expectedAccumulatedActivityDurations,
				expectedNegativeDurationsSequence);
	}

	public void testOpenLongEnough() {
		
		ActivityFacility facility = this.facilities.getFacilities().get(new IdImpl("1"));
		ActivityOption actOpt = facility.createActivityOption("home");
		actOpt.setCapacity(1.0);
		
		facility = this.facilities.getFacilities().get(new IdImpl("3"));
		actOpt = facility.createActivityOption("work_sector3");
		actOpt.addOpeningTime(new OpeningTimeImpl(DayType.wed, Time.parseTime("07:00:00"), Time.parseTime("18:00:00")));
		
		facility = this.facilities.getFacilities().get(new IdImpl("5"));
		actOpt = facility.createActivityOption("leisure");
		actOpt.addOpeningTime(new OpeningTimeImpl(DayType.wed, Time.parseTime("11:00:00"), Time.parseTime("16:00:00")));

		// []{end home, work_sector3, leisure, work_Sector3, start home, finish, reset}
		String[] expectedTooShortDurationsSequence = new String[]{"00:00:00", "00:00:00", "00:10:00",  "00:10:00", "00:10:00", "00:10:00", "00:00:00"};
		String[] expectedWaitingTimeSequence = new String[]{"00:00:00", "00:00:00", "00:00:00", "00:00:00", "00:00:00", "00:00:00", "00:00:00"};
		TreeMap<String, String[]> expectedAccumulatedActivityDurations = new TreeMap<String, String[]>();
		expectedAccumulatedActivityDurations.put("home", new String[]{null, null, null, null, "14:30:00", "14:30:00", null});
		expectedAccumulatedActivityDurations.put("work_sector3", new String[]{null, "06:00:00", "06:00:00", "08:00:00", "08:00:00", "08:00:00", null});
		expectedAccumulatedActivityDurations.put("leisure", new String[]{null, null, "00:20:00", "00:20:00", "00:20:00", "00:20:00", null});
		String[] expectedNegativeDurationsSequence = new String[]{"00:00:00", "00:00:00", "00:00:00", "00:00:00", "00:00:00", "00:00:00", "00:00:00"};
		this.runTest(
				expectedTooShortDurationsSequence, 
				expectedWaitingTimeSequence, 
				expectedAccumulatedActivityDurations,
				expectedNegativeDurationsSequence);

	}
	
	public void testWaiting() {
	
		ActivityFacility facility = this.facilities.getFacilities().get(new IdImpl("1"));
		ActivityOption actOpt = facility.createActivityOption("home");
		actOpt.setCapacity(1.0);

		facility = this.facilities.getFacilities().get(new IdImpl("3"));
		actOpt = facility.createActivityOption("work_sector3");
		actOpt.addOpeningTime(new OpeningTimeImpl(DayType.wed, Time.parseTime("07:00:00"), Time.parseTime("14:00:00")));
		actOpt.addOpeningTime(new OpeningTimeImpl(DayType.wed, Time.parseTime("15:15:00"), Time.parseTime("20:00:00")));
		
		facility = this.facilities.getFacilities().get(new IdImpl("5"));
		actOpt = facility.createActivityOption("leisure");
		actOpt.addOpeningTime(new OpeningTimeImpl(DayType.wed, Time.parseTime("11:00:00"), Time.parseTime("14:00:00")));

		// []{end home, work_sector3, leisure, work_Sector3, start home, finish, reset}
		String[] expectedTooShortDurationsSequence = new String[]{"00:00:00", "00:00:00", "00:10:00",  "00:10:00", "00:10:00", "00:10:00", "00:00:00"};
		String[] expectedWaitingTimeSequence = new String[]{"00:00:00", "00:30:00", "00:50:00", "01:05:00", "01:05:00", "01:05:00", "00:00:00"};
		TreeMap<String, String[]> expectedAccumulatedActivityDurations = new TreeMap<String, String[]>();
		expectedAccumulatedActivityDurations.put("home", new String[]{null, null, null, null, "14:30:00", "14:30:00", null});
		expectedAccumulatedActivityDurations.put("work_sector3", new String[]{null, "05:30:00", "05:30:00", "07:15:00", "07:15:00", "07:15:00", null});
		expectedAccumulatedActivityDurations.put("leisure", new String[]{null, null, "00:00:00", "00:00:00", "00:00:00", "00:00:00", null});
		String[] expectedNegativeDurationsSequence = new String[]{"00:00:00", "00:00:00", "00:00:00", "00:00:00", "00:00:00", "00:00:00", "00:00:00"};
		this.runTest(
				expectedTooShortDurationsSequence, 
				expectedWaitingTimeSequence, 
				expectedAccumulatedActivityDurations,
				expectedNegativeDurationsSequence);

	}
	
	public void testOverlapping() {
		
		ActivityFacility facility = this.facilities.getFacilities().get(new IdImpl("1"));
		ActivityOption actOpt = facility.createActivityOption("home");
		actOpt.setCapacity(1.0);

		facility = this.facilities.getFacilities().get(new IdImpl("3"));
		actOpt = facility.createActivityOption("work_sector3");
		actOpt.addOpeningTime(new OpeningTimeImpl(DayType.wed, Time.parseTime("07:00:00"), Time.parseTime("10:00:00")));
		actOpt.addOpeningTime(new OpeningTimeImpl(DayType.wed, Time.parseTime("10:30:00"), Time.parseTime("14:00:00")));
		actOpt.addOpeningTime(new OpeningTimeImpl(DayType.wed, Time.parseTime("15:15:00"), Time.parseTime("20:00:00")));
		
		facility = this.facilities.getFacilities().get(new IdImpl("5"));
		actOpt = facility.createActivityOption("leisure");
		actOpt.addOpeningTime(new OpeningTimeImpl(DayType.wed, Time.parseTime("11:00:00"), Time.parseTime("14:00:00")));

		// []{end home, work_sector3, leisure, work_Sector3, start home, finish, reset}
		String[] expectedTooShortDurationsSequence = new String[]{"00:00:00", "00:00:00", "00:10:00",  "00:10:00", "00:10:00", "00:10:00", "00:00:00"};
		String[] expectedWaitingTimeSequence = new String[]{"00:00:00", "01:00:00", "01:20:00", "01:35:00", "01:35:00", "01:35:00", "00:00:00"};
		TreeMap<String, String[]> expectedAccumulatedActivityDurations = new TreeMap<String, String[]>();
		expectedAccumulatedActivityDurations.put("home", new String[]{null, null, null, null, "14:30:00", "14:30:00", null});
		expectedAccumulatedActivityDurations.put("work_sector3", new String[]{null, "05:00:00", "05:00:00", "06:45:00", "06:45:00", "06:45:00", null});
		expectedAccumulatedActivityDurations.put("leisure", new String[]{null, null, "00:00:00", "00:00:00", "00:00:00", "00:00:00", null});
		String[] expectedNegativeDurationsSequence = new String[]{"00:00:00", "00:00:00", "00:00:00", "00:00:00", "00:00:00", "00:00:00", "00:00:00"};
		this.runTest(
				expectedTooShortDurationsSequence, 
				expectedWaitingTimeSequence, 
				expectedAccumulatedActivityDurations,
				expectedNegativeDurationsSequence);
		
	}
	
	protected void runTest(
			String[] expectedTooShortDurationsSequence, 
			String[] expectedWaitingTimeSequence, 
			TreeMap<String, String[]> expectedAccumulatedActivityDurations,
			String[] expectedNegativeDurationsSequence) {
		
		TreeMap<Id, FacilityPenalty> emptyFacilityPenalties = new TreeMap<Id, FacilityPenalty>();
		KTIYear3ScoringFunctionFactory factory = new KTIYear3ScoringFunctionFactory(config.charyparNagelScoring(), emptyFacilityPenalties);
		ScoringFunction testee = factory.getNewScoringFunction(this.plan);

		testee.endActivity(Time.parseTime("08:00:00"));
		
		assertEquals(0, factory.getActivities().getAccumulatedDurations().size());
		assertEquals(expectedTooShortDurationsSequence[0], Time.writeTime(factory.getActivities().getAccumulatedTooShortDuration()));
		assertEquals(expectedWaitingTimeSequence[0], Time.writeTime(factory.getActivities().getTimeSpentWaiting()));
		assertEquals(expectedNegativeDurationsSequence[0], Time.writeTime(factory.getActivities().getAccumulatedNegativeDuration()));
		
		testee.startLeg(Time.parseTime("08:00:00"), (Leg) this.plan.getPlanElements().get(1));
		testee.endLeg(Time.parseTime("08:30:00"));
		testee.startActivity(Time.parseTime("08:30:00"), (Activity) this.plan.getPlanElements().get(2));
		testee.endActivity(Time.parseTime("14:30:00"));

		for (String actType : expectedAccumulatedActivityDurations.keySet()) {
			if (factory.getActivities().getAccumulatedDurations().containsKey(actType)) {
				assertEquals(expectedAccumulatedActivityDurations.get(actType)[1], Time.writeTime(factory.getActivities().getAccumulatedDurations().get(actType)));
			}
		}
		assertEquals(expectedTooShortDurationsSequence[1], Time.writeTime(factory.getActivities().getAccumulatedTooShortDuration()));
		assertEquals(expectedWaitingTimeSequence[1], Time.writeTime(factory.getActivities().getTimeSpentWaiting()));
		assertEquals(expectedNegativeDurationsSequence[1], Time.writeTime(factory.getActivities().getAccumulatedNegativeDuration()));

		testee.startLeg(Time.parseTime("14:30:00"), (Leg) this.plan.getPlanElements().get(3));
		testee.endLeg(Time.parseTime("14:35:00"));
		testee.startActivity(Time.parseTime("14:35:00"), (Activity) this.plan.getPlanElements().get(4));
		testee.endActivity(Time.parseTime("14:55:00"));

		for (String actType : expectedAccumulatedActivityDurations.keySet()) {
			if (factory.getActivities().getAccumulatedDurations().containsKey(actType)) {
				assertEquals(expectedAccumulatedActivityDurations.get(actType)[2], Time.writeTime(factory.getActivities().getAccumulatedDurations().get(actType)));
			}
		}
		assertEquals(expectedTooShortDurationsSequence[2], Time.writeTime(factory.getActivities().getAccumulatedTooShortDuration()));
		assertEquals(expectedWaitingTimeSequence[2], Time.writeTime(factory.getActivities().getTimeSpentWaiting()));
		assertEquals(expectedNegativeDurationsSequence[2], Time.writeTime(factory.getActivities().getAccumulatedNegativeDuration()));
		
		testee.startLeg(Time.parseTime("14:55:00"), (Leg) this.plan.getPlanElements().get(5));
		testee.endLeg(Time.parseTime("15:00:00"));
		testee.startActivity(Time.parseTime("15:00:00"), (Activity) this.plan.getPlanElements().get(6));
		testee.endActivity(Time.parseTime("17:00:00"));

		for (String actType : expectedAccumulatedActivityDurations.keySet()) {
			if (factory.getActivities().getAccumulatedDurations().containsKey(actType)) {
				assertEquals(expectedAccumulatedActivityDurations.get(actType)[3], Time.writeTime(factory.getActivities().getAccumulatedDurations().get(actType)));
			}
		}
		assertEquals(expectedTooShortDurationsSequence[3], Time.writeTime(factory.getActivities().getAccumulatedTooShortDuration()));
		assertEquals(expectedWaitingTimeSequence[3], Time.writeTime(factory.getActivities().getTimeSpentWaiting()));
		assertEquals(expectedNegativeDurationsSequence[3], Time.writeTime(factory.getActivities().getAccumulatedNegativeDuration()));

		testee.startLeg(Time.parseTime("17:00:00"), (Leg) this.plan.getPlanElements().get(7));
		testee.endLeg(Time.parseTime("17:30:00"));
		testee.startActivity(Time.parseTime("17:30:00"), (Activity) this.plan.getPlanElements().get(8));

		for (String actType : expectedAccumulatedActivityDurations.keySet()) {
			if (factory.getActivities().getAccumulatedDurations().containsKey(actType)) {
				assertEquals(expectedAccumulatedActivityDurations.get(actType)[4], Time.writeTime(factory.getActivities().getAccumulatedDurations().get(actType)));
			}
		}
		assertEquals(expectedTooShortDurationsSequence[4], Time.writeTime(factory.getActivities().getAccumulatedTooShortDuration()));
		assertEquals(expectedWaitingTimeSequence[4], Time.writeTime(factory.getActivities().getTimeSpentWaiting()));
		assertEquals(expectedNegativeDurationsSequence[4], Time.writeTime(factory.getActivities().getAccumulatedNegativeDuration()));

		testee.finish();

		for (String actType : expectedAccumulatedActivityDurations.keySet()) {
			if (factory.getActivities().getAccumulatedDurations().containsKey(actType)) {
				assertEquals(expectedAccumulatedActivityDurations.get(actType)[5], Time.writeTime(factory.getActivities().getAccumulatedDurations().get(actType)));
			}
		}
		assertEquals(expectedTooShortDurationsSequence[5], Time.writeTime(factory.getActivities().getAccumulatedTooShortDuration()));
		assertEquals(expectedWaitingTimeSequence[5], Time.writeTime(factory.getActivities().getTimeSpentWaiting()));
		assertEquals(expectedNegativeDurationsSequence[5], Time.writeTime(factory.getActivities().getAccumulatedNegativeDuration()));

		double expectedScore = 0.0;
		expectedScore += factory.getParams().marginalUtilityOfEarlyDeparture * Time.parseTime(expectedTooShortDurationsSequence[5]);
		expectedScore += factory.getParams().marginalUtilityOfWaiting * Time.parseTime(expectedWaitingTimeSequence[5]);
		expectedScore += factory.getParams().marginalUtilityOfLateArrival * 2 * Time.parseTime(expectedNegativeDurationsSequence[5]);
		double duration, zeroUtilityDuration, typicalDuration;
		for (String actType : expectedAccumulatedActivityDurations.keySet()) {
			if (factory.getActivities().getAccumulatedDurations().containsKey(actType)) {
				typicalDuration = this.person.getDesires().getActivityDuration(actType);
				duration = Time.parseTime(expectedAccumulatedActivityDurations.get(actType)[5]);
				zeroUtilityDuration = (typicalDuration / 3600.0) * Math.exp( -10.0 / (typicalDuration / 3600.0) / ActivityScoringFunction.DEFAULT_PRIORITY);
				double utilPerf = factory.getParams().marginalUtilityOfPerforming * typicalDuration * Math.log((duration / 3600.0) / zeroUtilityDuration);
				double utilWait = factory.getParams().marginalUtilityOfWaiting * duration;
				expectedScore += Math.max(0, Math.max(utilPerf, utilWait));

			}
		}
		assertEquals(expectedScore, testee.getScore());
		
		testee.reset();

		for (String actType : expectedAccumulatedActivityDurations.keySet()) {
			if (factory.getActivities().getAccumulatedDurations().containsKey(actType)) {
				assertEquals(expectedAccumulatedActivityDurations.get(actType)[6], Time.writeTime(factory.getActivities().getAccumulatedDurations().get(actType)));
			}
		}
		assertEquals(expectedTooShortDurationsSequence[6], Time.writeTime(factory.getActivities().getAccumulatedTooShortDuration()));
		assertEquals(expectedWaitingTimeSequence[6], Time.writeTime(factory.getActivities().getTimeSpentWaiting()));
		assertEquals(expectedNegativeDurationsSequence[6], Time.writeTime(factory.getActivities().getAccumulatedNegativeDuration()));

		///////////////////////////////////////////////////////////////////////////////////////////////////
		// check zero utility durations
		///////////////////////////////////////////////////////////////////////////////////////////////////
		assertEquals(1.677557, factory.getActivities().getZeroUtilityDurations().get("work_sector3"), 1e-3);
		assertEquals(8.564183, factory.getActivities().getZeroUtilityDurations().get("home"), 1e-3);
		assertEquals(0.0, factory.getActivities().getZeroUtilityDurations().get("leisure"), 1e-3);
		
	}
	
	@Override
	protected void tearDown() throws Exception {
		super.tearDown();
		this.person = null;
		this.plan = null;
		this.config = null;
		this.facilities = null;
		this.network = null;
	}
	
}

package org.matsim.core.mobsim.jdeqsim;

import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.events.Event;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.api.core.v01.population.PlanElement;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.config.Config;
import org.matsim.core.controler.PrepareForSimUtils;
import org.matsim.core.events.EventsUtils;
import org.matsim.core.events.handler.BasicEventHandler;
import org.matsim.core.mobsim.qsim.QSim;
import org.matsim.core.mobsim.qsim.QSimBuilder;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.io.IOUtils;
import org.matsim.examples.ExamplesUtils;
import org.matsim.testcases.MatsimTestUtils;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class TestJDEQSimQsimConsistency {
	@Rule
	public final MatsimTestUtils utils = new MatsimTestUtils();

	@Test
	public void testSameEventTypeSequencePerAgent() {
		final Config config = utils.loadConfig(
				IOUtils.newUrl(ExamplesUtils.getTestScenarioURL("berlin"), "config.xml"));

		// fails with JDEQSim
		config.parallelEventHandling().setSynchronizeOnSimSteps(false);
		config.parallelEventHandling().setNumberOfThreads(1);

		final Scenario scenario = ScenarioUtils.loadScenario(config);
		PrepareForSimUtils.createDefaultPrepareForSim(scenario).run();

		//final Collection<Person> persons = new ArrayList<>(scenario.getPopulation().getPersons().values());
		final Collection<Person> persons =
				// handpicked IDs
				IntStream.of(77862, 80390)
						.mapToObj(Id::createPersonId)
						.map(scenario.getPopulation().getPersons()::get)
						.collect(Collectors.toList());

		for (Person person : persons) {
			scenario.getPopulation().getPersons().clear();
			scenario.getPopulation().addPerson(person);
			testSameSequenceOfEvents(scenario);
		}
	}


	private void testSameSequenceOfEvents(final Scenario scenario) {
		final Config config = scenario.getConfig();

		final EventStoringHandler jdeqSimEvents = new EventStoringHandler();
		final EventsManager jdeqSimEventsManager = EventsUtils.createEventsManager(config);
		jdeqSimEventsManager.addHandler(jdeqSimEvents);

		final EventStoringHandler qSimEvents = new EventStoringHandler();
		final EventsManager qSimEventsManager = EventsUtils.createEventsManager(config);
		qSimEventsManager.addHandler(qSimEvents);

		final QSim qSim = new QSimBuilder(config).useDefaults().build(scenario, qSimEventsManager);
		qSim.run();

		final JDEQSimulation jdeqSim = new JDEQSimulation(config.jdeqSim(), scenario, jdeqSimEventsManager);
		jdeqSim.run();

		final Person person = scenario.getPopulation().getPersons().values().iterator().next();
		final List<PlanElement> plan = person.getSelectedPlan().getPlanElements();

		Assert.assertEquals(
				"Unexpected sequence of event types for "+person+" with plan "+plan,
				qSimEvents.getEventTypes(),
				jdeqSimEvents.getEventTypes());
	}

	@Test
	public void testSameNumberOfTypesFullBerlin() {
		final Config config = utils.loadConfig(
				IOUtils.newUrl(ExamplesUtils.getTestScenarioURL("berlin"), "config.xml"));

		// fails with JDEQSim
		config.parallelEventHandling().setSynchronizeOnSimSteps(false);
		config.parallelEventHandling().setNumberOfThreads(1);

		final Scenario scenario = ScenarioUtils.loadScenario(config);
		// This creates vehicles for each agent, which is expected by QSim
		PrepareForSimUtils.createDefaultPrepareForSim(scenario).run();

		final EventStoringHandler jdeqSimEvents = new EventStoringHandler();
		final EventsManager jdeqSimEventsManager = EventsUtils.createEventsManager(config);
		jdeqSimEventsManager.addHandler(jdeqSimEvents);

		final EventStoringHandler qSimEvents = new EventStoringHandler();
		final EventsManager qSimEventsManager = EventsUtils.createEventsManager(config);
		qSimEventsManager.addHandler(qSimEvents);

		final QSim qSim = new QSimBuilder(config).useDefaults().build(scenario, qSimEventsManager);
		qSim.run();

		final JDEQSimulation jdeqSim = new JDEQSimulation(config.jdeqSim(), scenario, jdeqSimEventsManager);
		jdeqSim.run();

		// This is a very non precise test, but the probability to get exactly the same number of events with different
		// semantics on a scenario complex enough is low enough for it to be OK.
		// Testing it better would require testing the sequence of event types for each person, ignoring the time
		// (as the two mobsims are expected to give slightly different results), which is made complicated by the fact
		// that not all events are (explicitly) linked to a person.
		// This test, of course, does not fail if the same events are generated in a different sequence.
		Assert.assertEquals(
				"JDEQSim did not generate as many events as QSim",
				qSimEvents.getTypeCounts(),
				jdeqSimEvents.getTypeCounts());
	}

	private static class EventStoringHandler implements BasicEventHandler {
		private final List<Event> events = new ArrayList<>();

		@Override
		public void handleEvent(Event event) {
			events.add(event);
		}

		public Map<String, Long> getTypeCounts() {
			// TreeMap for consistent ordering
			return new TreeMap<>(events.stream().collect(
					Collectors.groupingBy(
							Event::getEventType,
							Collectors.counting())));
		}

		public List<String> getEventTypes() {
			return events.stream().map(Event::getEventType).collect(Collectors.toList());
		}
	}
}

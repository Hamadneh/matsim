/* *********************************************************************** *
 * project: org.matsim.*
 * AbstractHighestWeightSelector.java
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2012 by the members listed in the COPYING,        *
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
package playground.thibautd.socnetsim.replanning.selectors;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Plan;

import playground.thibautd.socnetsim.population.JointPlan;
import playground.thibautd.socnetsim.population.JointPlans;
import playground.thibautd.socnetsim.replanning.grouping.GroupPlans;
import playground.thibautd.socnetsim.replanning.grouping.ReplanningGroup;

/**
 * Selects the plan combination with the highest (implementation specific)
 * weight.
 * <br>
 * To do so, it iteratively constructs the joint plan using a branch-and-bound
 * approach, which avoids exploring the full set of combinations.
 * @author thibautd
 */
public abstract class AbstractHighestWeightSelector implements GroupLevelPlanSelector {
	private static final Logger log =
		Logger.getLogger(AbstractHighestWeightSelector.class);

	private static final double EPSILON = 1E-7;
	private final boolean forbidBlockingCombinations;
	private final boolean pruneSimilarBranches;
	private final boolean exploreAll;

	protected AbstractHighestWeightSelector() {
		this( false );
	}

	protected AbstractHighestWeightSelector(final boolean isForRemoval) {
		this( isForRemoval , false );
	}

	/**
	 * @param exploreAll for test purposes
	 */
	protected AbstractHighestWeightSelector(
			final boolean isForRemoval,
			final boolean exploreAll) {
		this( isForRemoval , exploreAll , true );
	}

	protected AbstractHighestWeightSelector(
			final boolean isForRemoval,
			final boolean exploreAll,
			final boolean pruneSimilarBranches) {
		this.forbidBlockingCombinations = isForRemoval;
		this.exploreAll = exploreAll;
		this.pruneSimilarBranches = pruneSimilarBranches;
	}

	// /////////////////////////////////////////////////////////////////////////
	// interface and abstract method
	// /////////////////////////////////////////////////////////////////////////
	@Override
	public final GroupPlans selectPlans(
			final JointPlans jointPlans,
			final ReplanningGroup group) {
		if (log.isTraceEnabled()) log.trace( "handling group "+group );
		Map<Id, PersonRecord> personRecords = getPersonRecords( jointPlans , group );

		GroupPlans allocation = selectPlans( personRecords );

		if (log.isTraceEnabled()) log.trace( "returning allocation "+allocation );
		return allocation;
	}

	/**
	 * Defines the weight of a plan, used for selection.
	 * The method is called once for each plan: it is not required that
	 * the method returns the same result if called twice with the same
	 * arguments (ie it can return a random number).
	 *
	 * @param indivPlan the plan to weight
	 * @param replanningGroup the group for which plans are being selected.
	 * Selectors using "niching" measures may need this. No modifications should
	 * be done to the group.
	 */
	public abstract double getWeight(
			final Plan indivPlan,
			final ReplanningGroup replanningGroup);

	// /////////////////////////////////////////////////////////////////////////
	// "translation" to and from the internal data structures
	// /////////////////////////////////////////////////////////////////////////
	private static GroupPlans toGroupPlans(final PlanString allocation) {
		Set<JointPlan> jointPlans = new HashSet<JointPlan>();
		List<Plan> individualPlans = new ArrayList<Plan>();
		for (PlanString curr = allocation;
				curr != null;
				curr = curr.tail) {
			if (curr.planRecord.jointPlan != null) {
				jointPlans.add( curr.planRecord.jointPlan );
			}
			else {
				individualPlans.add( curr.planRecord.plan );
			}
		}

		return new GroupPlans( jointPlans , individualPlans );
	}

	private Map<Id, PersonRecord> getPersonRecords(
			final JointPlans jointPlans,
			final ReplanningGroup group) {
		final Map<Id, PersonRecord> map = new LinkedHashMap<Id, PersonRecord>();
		final Map<Plan, Double> weights = new HashMap<Plan, Double>();

		for (Person person : group.getPersons()) {
			for (Plan plan : person.getPlans()) {
				final double w = getWeight( plan , group );
				if ( Double.isNaN( w ) ) throw new IllegalArgumentException( "NaN weights are not allowed" );
				weights.put( plan , w );
			}
		}

		for (Person person : group.getPersons()) {
			final LinkedList<PlanRecord> plans = new LinkedList<PlanRecord>();
			for (Plan plan : person.getPlans()) {
				double w = weights.get( plan );
				final JointPlan jp = jointPlans.getJointPlan( plan );

				if (jp != null) {
					for (Plan p : jp.getIndividualPlans().values()) {
						if (p == plan) continue;
						w += weights.get( p );
					}
					w /= jp.getIndividualPlans().size();
				}
				
				plans.add( new PlanRecord(
							plan,
							jp,
							w));
			}
			map.put(
					person.getId(),
					new PersonRecord( person , plans ) );
		}

		for (PersonRecord personRecord : map.values()) {
			Collections.sort(
					personRecord.plans,
					new Comparator<PlanRecord>() {
						@Override
						public int compare(
								final PlanRecord o1,
								final PlanRecord o2) {
							// sort in DECREASING order
							return -Double.compare( o1.avgJointPlanWeight , o2.avgJointPlanWeight );
						}
					});
		}

		return map;
	}

	// /////////////////////////////////////////////////////////////////////////
	// "outer loop": search and forbid if blocking (if forbid blocking is true)
	// /////////////////////////////////////////////////////////////////////////
	private GroupPlans selectPlans( final Map<Id, PersonRecord> personRecords ) {
		final ForbidenCombinations forbiden = new ForbidenCombinations();

		GroupPlans plans = null;

		int count = 0;
		do {
			count++;
			final PlanString allocation = buildPlanString(
				forbiden,
				personRecords,
				new ArrayList<PersonRecord>( personRecords.values() ),
				Collections.<Id>emptySet(),
				null,
				Double.NEGATIVE_INFINITY);

			plans = allocation == null ? null : toGroupPlans( allocation );
		} while (
				plans != null &&
				continueIterations( forbiden , personRecords , plans ) );

		assert forbidBlockingCombinations || count == 1 : count;
		assert plans == null || !forbiden.isForbidden( plans );

		return plans;
	}

	private boolean continueIterations(
			final ForbidenCombinations forbiden,
			final Map<Id, PersonRecord> personRecords,
			final GroupPlans allocation) {
		if ( !forbidBlockingCombinations ) return false;

		if (log.isTraceEnabled()) log.trace( "checking if need to continue" );
		assert !forbiden.isForbidden( allocation ) : "forbidden combination was re-examined";

		if (isBlocking( personRecords, allocation )) {
			if (log.isTraceEnabled()) {
				log.trace( allocation+" is blocking" );
			}

			forbiden.forbid( allocation );
			return true;
		}

		if (log.isTraceEnabled()) {
			log.trace( allocation+" is not blocking" );
		}

		return false;
	}

	private boolean isBlocking(
			final Map<Id, PersonRecord> personRecords,
			final GroupPlans groupPlan) {
		return !searchForCombinationsWithoutForbiddenPlans(
				groupPlan,
				personRecords,
				new ArrayList<PersonRecord>( personRecords.values() ),
				Collections.<Id> emptySet());
	}

	private boolean searchForCombinationsWithoutForbiddenPlans(
			final GroupPlans forbidenPlans,
			final Map<Id, PersonRecord> allPersonsRecord,
			final List<PersonRecord> personsStillToAllocate,
			final Set<Id> alreadyAllocatedPersons) {
		final PersonRecord currentPerson = personsStillToAllocate.get(0);

		// do one step forward: "point" to the next person
		final List<PersonRecord> remainingPersons =
			personsStillToAllocate.size() > 1 ?
			personsStillToAllocate.subList( 1, personsStillToAllocate.size() ) :
			Collections.<PersonRecord> emptyList();

		List<PlanRecord> records = new ArrayList<PlanRecord>( currentPerson.plans );

		final KnownBranches knownBranches = new KnownBranches( pruneSimilarBranches );
		for (PlanRecord r : records) {
			// skip forbidden plans
			if ( r.jointPlan == null &&
					forbidenPlans.getIndividualPlans().contains( r.plan ) ) {
				continue;
			}
			if ( r.jointPlan != null &&
					forbidenPlans.getJointPlans().contains( r.jointPlan ) ) {
				continue;
			}

			final Set<Id> cotravelers = r.jointPlan == null ? null : r.jointPlan.getIndividualPlans().keySet();
			if ( knownBranches.isExplored( cotravelers ) ) continue;

			List<PersonRecord> actuallyRemainingPersons = remainingPersons;
			Set<Id> actuallyAllocatedPersons = new HashSet<Id>(alreadyAllocatedPersons);
			actuallyAllocatedPersons.add( currentPerson.person.getId() );
			if (r.jointPlan != null) {
				if ( contains( r.jointPlan , alreadyAllocatedPersons ) ) continue;
				actuallyRemainingPersons = filter( remainingPersons , r.jointPlan );
				actuallyAllocatedPersons = new HashSet<Id>( actuallyAllocatedPersons );
				actuallyAllocatedPersons.addAll( r.jointPlan.getIndividualPlans().keySet() );
			}

			if ( !actuallyRemainingPersons.isEmpty() ) {
				final boolean found = searchForCombinationsWithoutForbiddenPlans(
						forbidenPlans,
						allPersonsRecord,
						actuallyRemainingPersons,
						actuallyAllocatedPersons);
				if (found) return true;
				// if we are here, it is impossible to find allowed plans with the remaining
				// agents. No need to re-explore.
				knownBranches.tagAsExplored( cotravelers );
			}
			else {
				return true;
			}
		}

		return false;
	}

	// /////////////////////////////////////////////////////////////////////////
	// actual branching and bounding methods
	// /////////////////////////////////////////////////////////////////////////
	/**
	 * Recursively decends in the tree of possible joint plans.
	 *
	 * @param allPersonRecord helper map, just links persons to ids
	 * @param personsStillToAllocate in the name
	 * @param alreadyAllocatedPersons set of the ids of the already allocated persons,
	 * used to determine which joint plans are stil possible
	 * @param str the PlanString of the plan constructed until now
	 */
	private PlanString buildPlanString(
			final ForbidenCombinations forbidenCombinations,
			final Map<Id, PersonRecord> allPersonsRecord,
			final List<PersonRecord> personsStillToAllocate,
			final Set<Id> alreadyAllocatedPersons,
			final PlanString str,
			final double minimalWeightToObtain) {
		final PersonRecord currentPerson = personsStillToAllocate.get(0);

		assert !alreadyAllocatedPersons.contains( currentPerson.person.getId() ) :
			"still to allocate: "+personsStillToAllocate+
			 ", already allocated: "+alreadyAllocatedPersons;
		if (log.isTraceEnabled()) {
			log.trace( "looking at person "+currentPerson.person.getId()+
					" with already selected "+alreadyAllocatedPersons );
		}

		// do one step forward: "point" to the next person
		final List<PersonRecord> remainingPersons =
			personsStillToAllocate.size() > 1 ?
			personsStillToAllocate.subList( 1, personsStillToAllocate.size() ) :
			Collections.<PersonRecord> emptyList();
		final Set<Id> newAllocatedPersons = new HashSet<Id>(alreadyAllocatedPersons);
		newAllocatedPersons.add( currentPerson.person.getId() );

		// get a list of plans in decreasing order of maximum possible weight.
		// The weight is always computed on the full joint plan, and thus consists
		// of the weight until now plus the upper bound
		final List<PlanRecord> records = new ArrayList<PlanRecord>( currentPerson.plans );
		final double alreadyAllocatedWeight = str == null ? 0 : str.getWeight();
		for (PlanRecord r : records) {
			r.cachedMaximumWeight = exploreAll ?
				Double.POSITIVE_INFINITY :
				alreadyAllocatedWeight +
					getMaxWeightFromPersons(
							r,
							newAllocatedPersons,
							remainingPersons );
		}

		// Sort in decreasing order of upper bound: we can stop as soon
		// as the constructed plan has weight greater than the upper bound
		// of the next branch.
		Collections.sort(
				records,
				new Comparator<PlanRecord>() {
					@Override
					public int compare(
							final PlanRecord o1,
							final PlanRecord o2) {
						// sort in DECREASING order
						return -Double.compare(
							o1.cachedMaximumWeight,
							o2.cachedMaximumWeight );
					}
				});

		// get the actual allocation, and stop when the allocation
		// is better than the maximum possible in remaining plans
		// or worst than the worst possible at a higher level
		PlanString constructedString = null;
		// the plans got after this step only depend on the agents still to
		// allocate. We can stop at the first found solution.
		final KnownBranches knownBranches = new KnownBranches( pruneSimilarBranches );

		for (PlanRecord r : records) {
			if (!exploreAll &&
					constructedString != null &&
					r.cachedMaximumWeight <= constructedString.getWeight()) {
				if (log.isTraceEnabled()) {
					log.trace( "maximum weight from now on: "+r.cachedMaximumWeight );
					log.trace( "weight obtained: "+constructedString.getWeight() );
					log.trace( " => CUTOFF by upper bound" );
				}
				break;
			}

			if (!exploreAll && r.cachedMaximumWeight < minimalWeightToObtain) {
				if (log.isTraceEnabled()) {
					log.trace( "maximum weight from now on: "+r.cachedMaximumWeight );
					log.trace( "minimum weight to obtain: "+minimalWeightToObtain );
					log.trace( " => CUTOFF by lower bound" );
				}
				break;
			}

			final Set<Id> cotravelers = r.jointPlan == null ? null : r.jointPlan.getIndividualPlans().keySet();
			if ( knownBranches.isExplored( cotravelers ) ) continue;

			PlanString tail = str;
			// TODO: find a better way to filter persons (should be
			// possible in PlanString), ie without having to create new collections
			// over and over, which is messy and inefficient
			List<PersonRecord> actuallyRemainingPersons = remainingPersons;
			Set<Id> actuallyAllocatedPersons = newAllocatedPersons;
			if (r.jointPlan != null) {
				// normally, it is impossible that it is always the case if there
				// is a valid plan: a branch were this would be the case would
				// have a infinitely negative weight and not explored.
				if ( contains( r.jointPlan , alreadyAllocatedPersons ) ) continue;
				tail = getOtherPlansAsString( r , allPersonsRecord , tail);
				actuallyRemainingPersons = filter( remainingPersons , r.jointPlan );
				actuallyAllocatedPersons = new HashSet<Id>( newAllocatedPersons );
				actuallyAllocatedPersons.addAll( r.jointPlan.getIndividualPlans().keySet() );
			}

			PlanString newString;
			if ( !actuallyRemainingPersons.isEmpty() ) {
				newString = buildPlanString(
						forbidenCombinations,
						allPersonsRecord,
						actuallyRemainingPersons,
						actuallyAllocatedPersons,
						new PlanString( r , tail ),
						Math.max(
							minimalWeightToObtain,
							constructedString != null ?
								constructedString.getWeight() - EPSILON :
								Double.NEGATIVE_INFINITY));
				// if we found something, it is the best given the joint structure
				// (plans are sorted by avg joint plan weight): do not search more
				// for this particular structure.
				// If we did not found something, trying again with the same structure
				// will not change anything.
				knownBranches.tagAsExplored( cotravelers );
			}
			else {
				newString = new PlanString( r , tail );

				if ( forbidBlockingCombinations && forbidenCombinations.isForbidden( newString ) ) {
					// we are on a leaf (ie a full plan).
					// If some combinations are forbidden, check if this one is.
					if ( log.isTraceEnabled() ) log.trace( "skipping forbiden string "+newString );
					newString = null;
				}
			}

			if (newString == null) continue;

			assert newString.getWeight() <= r.cachedMaximumWeight :
				getClass()+" weight higher than estimated max: "+newString.getWeight()+" > "+r.cachedMaximumWeight;

			if (constructedString == null ||
					newString.getWeight() > constructedString.getWeight()) {
				constructedString = newString;
				if (log.isTraceEnabled()) log.trace( "new string "+constructedString+" with weight "+constructedString.getWeight() );
			}
			else if (log.isTraceEnabled()) {
				log.trace( "string "+newString+" with weight "+newString.getWeight()+" did not improve" );
			}
		}

		return constructedString;
	}

	/**
	 * Gets the maximum plan weight that can be obtained from the
	 * plans of remainingPersons, given the alradySelected has been
	 * selected, and that planToSelect is about to be selected.
	 */
	private static double getMaxWeightFromPersons(
			final PlanRecord planToSelect,
			// the joint plans linking to persons with a plan
			// already selected cannot be selected.
			// This list contains the agent to be selected.
			final Collection<Id> personsSelected,
			final List<PersonRecord> remainingPersons) {
		double score = planToSelect.avgJointPlanWeight;

		// if the plan to select is a joint plan,
		// we know exactly what plan to get the score from.
		final JointPlan jointPlanToSelect = planToSelect.jointPlan;

		for (PersonRecord record : remainingPersons) {
			final double max = getMaxWeight( record , personsSelected , jointPlanToSelect );
			if (log.isTraceEnabled()) {
				log.trace( "estimated max weight for person "+
						record.person.getId()+
						" is "+max );
			}
			// if negative, no need to continue
			// moreover, returning here makes sure the branch has infinitely negative
			// weight, even if plans in it have infinitely positive weights
			if (max == Double.NEGATIVE_INFINITY) return Double.NEGATIVE_INFINITY;
			// avoid making the bound too tight, to avoid messing up with the rounding error
			score += max + EPSILON;
		}

		return score;
	}

	/**
	 * @return the highest weight of a plan wich does not pertains to a joint
	 * plan shared with agents in personsSelected
	 */
	private static double getMaxWeight(
			final PersonRecord record,
			final Collection<Id> personsSelected,
			final JointPlan jointPlanToSelect) {
		// case in jp: plan is fully determined
		if (jointPlanToSelect != null) {
			final Plan plan = jointPlanToSelect.getIndividualPlan( record.person.getId() );
			if (plan != null) return record.getRecord( plan ).avgJointPlanWeight;
		}

		final Collection<Id> idsInJpToSelect =
			jointPlanToSelect == null ?
			Collections.<Id> emptySet() :
			jointPlanToSelect.getIndividualPlans().keySet();

		for (PlanRecord plan : record.plans) {
			// the plans are sorted by decreasing weight:
			// consider the first valid plan

			if (plan.jointPlan == null) return plan.avgJointPlanWeight;

			// skip this plan if its participants already have a plan
			if (contains( plan.jointPlan , personsSelected )) continue;
			if (contains( plan.jointPlan , idsInJpToSelect )) continue;
			return plan.avgJointPlanWeight;
		}

		// this combination is impossible
		return Double.NEGATIVE_INFINITY;
	}

	// /////////////////////////////////////////////////////////////////////////
	// various small helper methods
	// /////////////////////////////////////////////////////////////////////////
	private static List<PersonRecord> filter(
			final List<PersonRecord> toFilter,
			final JointPlan jointPlan) {
		List<PersonRecord> newList = new ArrayList<PersonRecord>();

		for (PersonRecord r : toFilter) {
			if (!jointPlan.getIndividualPlans().containsKey( r.person.getId() )) {
				newList.add( r );
			}
		}

		return newList;
	}

	private static PlanString getOtherPlansAsString(
			final PlanRecord r,
			final Map<Id, PersonRecord> allPersonsRecords,
			final PlanString additionalTail) {
		PlanString str = additionalTail;

		for (Plan p : r.jointPlan.getIndividualPlans().values()) {
			if (p == r.plan) continue;

			str = new PlanString(
					allPersonsRecords.get( p.getPerson().getId() ).getRecord( p ),
					str);
		}

		return str;
	}

	private static boolean contains(
			final JointPlan jp,
			final Collection<Id> personsSelected) {
		for (Id id : personsSelected) {
			if (jp.getIndividualPlans().containsKey( id )) return true;
		}
		return false;
	}

	// /////////////////////////////////////////////////////////////////////////
	// classes: data structures used during the search process
	// /////////////////////////////////////////////////////////////////////////

	private static final class PlanString {
		public final PlanRecord planRecord;
		public final PlanString tail;
		private final double weight;

		public PlanString(
				final PlanRecord head,
				final PlanString tail) {
			this.planRecord = head;
			this.tail = tail;
			this.weight = head.avgJointPlanWeight + (tail == null ? 0 : tail.getWeight());
		}

		public double getWeight() {
			return weight;
		}

		@Override
		public String toString() {
			return "("+planRecord+"; "+tail+")";
		}
	}

	private static class PersonRecord {
		final Person person;
		final LinkedList<PlanRecord> plans;

		public PersonRecord(
				final Person person,
				final LinkedList<PlanRecord> plans) {
			this.person = person;
			this.plans = plans;
			Collections.sort(
					this.plans,
					new Comparator<PlanRecord>() {
						@Override
						public int compare(
								final PlanRecord o1,
								final PlanRecord o2) {
							// sort in DECREASING order
							return -Double.compare( o1.avgJointPlanWeight , o2.avgJointPlanWeight );
						}
					});
		}

		public PlanRecord getRecord( final Plan plan ) {
			for (PlanRecord r : plans) {
				if (r.plan == plan) return r;
			}
			throw new IllegalArgumentException();
		}

		@Override
		public String toString() {
			return "{PersonRecord: person="+person+"; plans="+plans+"}";
		}
	}

	private static class PlanRecord {
		final Plan plan;
		/**
		 * The joint plan to which pertains the individual plan,
		 * if any.
		 */
		final JointPlan jointPlan;
		final double avgJointPlanWeight;
		double cachedMaximumWeight = Double.NaN;

		public PlanRecord(
				final Plan plan,
				final JointPlan jointPlan,
				final double weight) {
			this.plan = plan;
			this.jointPlan = jointPlan;
			this.avgJointPlanWeight = weight;
		}

		@Override
		public String toString() {
			return "{PlanRecord: "+plan.getPerson().getId()+":"+plan.getScore()+
				" linkedWith:"+(jointPlan == null ? "[]" : jointPlan.getIndividualPlans().keySet())+
				" weight="+avgJointPlanWeight+"}";
		}
	}

	private static class ForbidenCombinations {
		private final List<GroupPlans> forbidden = new ArrayList<GroupPlans>();

		public void forbid(final GroupPlans plans) {
			forbidden.add( plans );
		}

		public boolean isForbidden(final PlanString ps) {
			for (GroupPlans p : forbidden) {
				if ( forbids( p , ps ) ) return true;
			}
			return false;
		}

		public boolean isForbidden(final GroupPlans groupPlans) {
			return forbidden.contains( groupPlans );
		}

		private static boolean forbids(
				final GroupPlans forbidden,
				final PlanString string) {
			PlanString tail = string;

			// check if all plans in the string are in the groupPlans
			// copying the list and removing the elements is much faster
			// than using "contains" on big lists.
			final List<Plan> plans = new ArrayList<Plan>( forbidden.getIndividualPlans() );
			while (tail != null) {
				final PlanRecord head = tail.planRecord;
				tail = tail.tail;

				if (head.jointPlan != null &&
						!forbidden.getJointPlans().contains( head.jointPlan )) {
					return false;
				}

				if (head.jointPlan == null &&
						!plans.remove( head.plan )) {
					assert !forbidden.getIndividualPlans().contains( head.plan ) : "planString contains duplicates";
					return false;
				}
			}

			return true;
		}
	}

	private static class KnownBranches {
		private final boolean prune;
		private final List<Set<Id>> branches = new ArrayList<Set<Id>>();

		public KnownBranches(final boolean prune) {
			this.prune = prune;
		}

		public void tagAsExplored(final Set<Id> branch) {
			if (prune) branches.add( branch );
		}

		public boolean isExplored(final Set<Id> branch) {
			return prune && branches.contains( branch );
		}
	}
}

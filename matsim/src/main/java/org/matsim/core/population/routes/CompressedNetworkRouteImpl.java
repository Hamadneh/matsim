/* *********************************************************************** *
 * project: org.matsim.*
 * CompressedRoute.java
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

package org.matsim.core.population.routes;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Node;

/**
 * Implementation of {@link NetworkRouteWRefs} that tries to minimize the amount of
 * data needed to be stored for each route. This will give some memory savings,
 * allowing for larger scenarios (=more agents), especially on detailed
 * networks, but is likely a bit slower due to the more complex access of the
 * route information internally.
 *
 * @author mrieser
 */
public class CompressedNetworkRouteImpl extends AbstractRoute implements NetworkRouteWRefs, Cloneable {

	private final static Logger log = Logger.getLogger(CompressedNetworkRouteImpl.class);

	private ArrayList<Link> route = new ArrayList<Link>(0);
	private final Map<Link, Link> subsequentLinks;
	private double travelCost = Double.NaN;
	/** number of links in uncompressed route */
	private int uncompressedLength = -1;
	private int modCount = 0;
	private int routeModCountState = 0;
	private Id vehicleId = null;

	public CompressedNetworkRouteImpl(final Link startLink, final Link endLink, final Map<Link, Link> subsequentLinks) {
		super(startLink, endLink);
		this.subsequentLinks = subsequentLinks;
	}

	@Override
	public CompressedNetworkRouteImpl clone() {
		CompressedNetworkRouteImpl cloned = (CompressedNetworkRouteImpl) super.clone();
		ArrayList<Link> tmpRoute = cloned.route;
		cloned.route = new ArrayList<Link>(tmpRoute); // deep copy
		return cloned;
	}

	@Deprecated
	@Override
	public List<Link> getLinks() {
		if (this.uncompressedLength < 0) { // it seems the route never got initialized correctly
			return new ArrayList<Link>(0);
		}
		ArrayList<Link> links = new ArrayList<Link>(this.uncompressedLength);
		if (this.modCount != this.routeModCountState) {
			log.error("Route was modified after storing it! modCount=" + this.modCount + " routeModCount=" + this.routeModCountState);
			return links;
		}
		Link previousLink = getStartLink();
		Link endLink = getEndLink();
		if (previousLink == endLink) {
			return links;
		}
		for (Link link : this.route) {
			getLinksTillLink(links, link, previousLink);
			links.add(link);
			previousLink = link;
		}
		getLinksTillLink(links, endLink, previousLink);

		return links;
	}

	private void getLinksTillLink(final List<Link> links, final Link nextLink, final Link startLink) {
		Link link = startLink;
		while (true) { // loop until we hit "return;"
			for (Link outLink : link.getToNode().getOutLinks().values()) {
				if (outLink == nextLink) { // TODO [MR] check for performance improvement: if link.getToNode == nextLink.getFromNode
					return;
				}
			}
			link = this.subsequentLinks.get(link);
			links.add(link);
		}
	}

	@Override
	public void setEndLink(final Link link) {
		this.modCount++;
		super.setEndLink(link);
	}

	@Override
	public void setStartLink(final Link link) {
		this.modCount++;
		super.setStartLink(link);
	}

	@Override
	public List<Id> getLinkIds() {
		List<Link> links = getLinks();
		List<Id> ids = new ArrayList<Id>(links.size());
		for (Link link : links) {
			ids.add(link.getId());
		}
		return ids;
	}

	@Override
	public NetworkRouteWRefs getSubRoute(final Node fromNode, final Node toNode) {
		Link newStartLink = null;
		Link newEndLink = null;
		List<Link> newLinks = new ArrayList<Link>(10);

		Link startLink = getStartLink();
		if (startLink.getToNode() == fromNode) {
			newStartLink = startLink;
		}
		for (Link link : getLinks()) {
			if (link.getFromNode() == toNode) {
				newEndLink = link;
				break;
			}
			if (newStartLink != null) {
				newLinks.add(link);
			}
			if (link.getToNode() == fromNode) {
				newStartLink = link;
			}
		}
		if (newStartLink == null) {
			throw new IllegalArgumentException("fromNode is not part of this route.");
		}
		if (newEndLink == null) {
			if (getEndLink().getFromNode() == toNode) {
				newEndLink = getEndLink();
			} else {
				throw new IllegalArgumentException("toNode is not part of this route.");
			}
		}

		NetworkRouteWRefs subRoute = new CompressedNetworkRouteImpl(newStartLink, newEndLink, this.subsequentLinks);
		subRoute.setLinks(newStartLink, newLinks, newEndLink);
		return subRoute;
	}

	@Override
	public double getTravelCost() {
		return this.travelCost;
	}

	@Override
	public void setTravelCost(final double travelCost) {
		this.travelCost = travelCost;
	}

	@Override
	public void setLinks(final Link startLink, final List<Link> srcRoute, final Link endLink) {
		this.route.clear();
		setStartLink(startLink);
		setEndLink(endLink);
		this.routeModCountState = this.modCount;
		if ((srcRoute == null) || (srcRoute.size() == 0)) {
			this.uncompressedLength = 0;
			return;
		}
		Link previousLink = startLink;
		for (Link link : srcRoute) {
			if (!this.subsequentLinks.get(previousLink).equals(link)) {
				this.route.add(link);
			}
			previousLink = link;
		}
		this.route.trimToSize();
		this.uncompressedLength = srcRoute.size();
//		System.out.println("uncompressed size: \t" + this.uncompressedLength + "\tcompressed size: \t" + this.route.size());
	}

	@Override
	public double getDistance() {
		double dist = super.getDistance();
		if (Double.isNaN(dist)) {
			dist = calcDistance();
		}
		return dist;
	}

	private double calcDistance() {
		if (this.modCount != this.routeModCountState) {
			log.error("Route was modified after storing it! modCount=" + this.modCount + " routeModCount=" + this.routeModCountState);
			return 99999.999;
		}
		double dist = 0;
		for (Link link : getLinks()) {
			dist += link.getLength();
		}
		setDistance(dist);
		return dist;
	}

	@Override
	public Id getVehicleId() {
		return this.vehicleId;
	}

	@Override
	public void setVehicleId(final Id vehicleId) {
		this.vehicleId = vehicleId;
	}

}

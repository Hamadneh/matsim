/* *********************************************************************** *
 * project: org.matsim.*
 * SignalGroupsReader20
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2010 by the members listed in the COPYING,        *
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
package org.matsim.contrib.signals.data.signalgroups.v20;

import java.io.IOException;
import java.io.InputStream;

import javax.xml.XMLConstants;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import javax.xml.validation.SchemaFactory;

import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.contrib.signals.model.Signal;
import org.matsim.contrib.signals.model.SignalGroup;
import org.matsim.contrib.signals.model.SignalSystem;
import org.matsim.core.api.internal.MatsimReader;
import org.matsim.core.utils.io.IOUtils;
import org.matsim.core.utils.io.UncheckedIOException;
import org.matsim.jaxb.signalgroups20.XMLIdRefType;
import org.matsim.jaxb.signalgroups20.XMLSignalGroupType;
import org.matsim.jaxb.signalgroups20.XMLSignalGroups;
import org.matsim.jaxb.signalgroups20.XMLSignalSystemSignalGroupType;
import org.xml.sax.SAXException;

/**
 * @author dgrether
 * @author jbischoff
 */
public class SignalGroupsReader20 implements MatsimReader {

	private SignalGroupsData signalGroupsData;
	private SignalGroupsDataFactory factory;
	private static final Logger log = Logger.getLogger(SignalGroupsReader20.class);

	public SignalGroupsReader20(SignalGroupsData signalGroupsData) {
		this.signalGroupsData = signalGroupsData;
		this.factory = signalGroupsData.getFactory();
	}

	@Override
	public void readFile(String filename) {
		XMLSignalGroups xmlsgdefs = readXmlSignalGroups(filename);

		for (XMLSignalSystemSignalGroupType xsssgt : xmlsgdefs.getSignalSystem()) {
			// SigSys
			for (XMLSignalGroupType xsgt : xsssgt.getSignalGroup()) {
				SignalGroupData sgd = factory.createSignalGroupData(Id.create(xsssgt.getRefId(), SignalSystem.class), Id.create(xsgt.getId(), SignalGroup.class));

				for (XMLIdRefType id : xsgt.getSignal()) {
					sgd.addSignalId(Id.create(id.getRefId(), Signal.class));
				}
				signalGroupsData.addSignalGroupData(sgd);
			}
		}
	}

	private XMLSignalGroups readXmlSignalGroups(String filename) {
		try (InputStream stream = IOUtils.getInputStream(filename)) {
			JAXBContext jc = JAXBContext.newInstance(org.matsim.jaxb.signalgroups20.ObjectFactory.class);
			Unmarshaller u = jc.createUnmarshaller();
			// validate XML file
			log.info("starting to validate " + filename);
			u.setSchema(SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI).newSchema(getClass().getResource("/dtd/signalGroups_v2.0.xsd")));
			log.info("starting unmarshalling " + filename);
			return (XMLSignalGroups) u.unmarshal(stream);
		} catch (IOException | JAXBException | SAXException e) {
			throw new UncheckedIOException(e);
		}
	}

}

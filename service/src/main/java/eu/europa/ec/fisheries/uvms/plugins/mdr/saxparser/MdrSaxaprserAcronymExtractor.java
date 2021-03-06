/*
Developed by the European Commission - Directorate General for Maritime Affairs and Fisheries @ European Union, 2015-2016.

This file is part of the Integrated Fisheries Data Management (IFDM) Suite. The IFDM Suite is free software: you can redistribute it
and/or modify it under the terms of the GNU General Public License as published by the Free Software Foundation, either version 3 of
the License, or any later version. The IFDM Suite is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for more
details. You should have received a copy of the GNU General Public License along with the IFDM Suite. If not, see <http://www.gnu.org/licenses/>.

*/
package eu.europa.ec.fisheries.uvms.plugins.mdr.saxparser;

import java.io.IOException;
import java.io.StringReader;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

public class MdrSaxaprserAcronymExtractor extends DefaultHandler {

    final static Logger LOG = LoggerFactory.getLogger(MdrSaxaprserAcronymExtractor.class);

    private static final String FA_QUERY_UUID_CONTAINER_TAG = "ns3:MDRQuery";

    private static final String ID_TAG = "ID";
    private static final String ID_TAG_FOR_SUBJ_QUER_ENTITY = "ID";
    private static final String UUID_ATTRIBUTE = "UUID";

    private String uuid;
    private boolean isStartOfInterestedTag;
    private boolean isIDStart;
    private boolean isUUIDStart;
    private String uuidValue; // store FLUXReportDocument UUID value inside this

    // Three case here : FaReportMessage, FaQueryMessage, FLUXResponseMessage
    private String CONTAINER_TAG;


    private MdrSaxaprserAcronymExtractor() {
        super();
    }

    public MdrSaxaprserAcronymExtractor(MdrType type) {
        switch (type) {
            case MDR_QUERY:
                CONTAINER_TAG = FA_QUERY_UUID_CONTAINER_TAG;
                break;
        }
    }

    /**
     * This method parse input document using SAX parser
     *
     * @param message
     * @throws SAXException
     */
    public void parseDocument(String message) throws SAXException {
        SAXParserFactory factory = SAXParserFactory.newInstance();
        SAXParser parser;
        try {
            parser = factory.newSAXParser();
            StringReader sr = new StringReader(message);
            InputSource source = new InputSource(sr);
            parser.parse(source, this);
        } catch (ParserConfigurationException e) {
            LOG.error("Parse exception while trying to parse incoming message from flux.", e);
        } catch (IOException e) {
            LOG.error("IOException while trying to parse incoming message from flux.", e);
        }
    }


    @Override
    public void startElement(String s, String s1, String elementName, Attributes attributes) throws SAXException {
        // We need to extract UUID value for FLUXReportDocument. So, Mark when the tag is found.
        if (CONTAINER_TAG.equals(elementName)) {
            isStartOfInterestedTag = true;
            LOG.debug("FLUXReportDocument tag found.");
        }
        if (isStartOfInterestedTag && (ID_TAG.equals(elementName) || ID_TAG_FOR_SUBJ_QUER_ENTITY.equals(elementName))) {
            isIDStart = true;
            LOG.debug("Found ID tag inside FLUXReportDocument tag");
            String value = attributes.getValue("schemeID");
            if (UUID_ATTRIBUTE.equals(value)) {
                LOG.debug("Found UUID schemeID inside ID tag");
                isUUIDStart = true;
            }
        }

    }

    @Override

    public void endElement(String s, String s1, String element) {
        if (CONTAINER_TAG.equals(element)) {
            isStartOfInterestedTag = false;
            LOG.debug("FLUXReportDocument tag Ended.");
        }
        if (ID_TAG.equals(element)) {
            isIDStart = false;
            isUUIDStart = false;
            LOG.debug("ID tag Ended.");
        }
    }

    @Override
    public void characters(char[] ac, int i, int j) throws SAXException {
        String tmpValue = new String(ac, i, j);
        if (isUUIDStart) {
            uuidValue = tmpValue;
            throw new SAXException("Found the required value . so, stop parsing entire document");
        }
    }


    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    public boolean isStartOfInterestedTag() {
        return isStartOfInterestedTag;
    }

    public void setStartOfInterestedTag(boolean startOfInterestedTag) {
        isStartOfInterestedTag = startOfInterestedTag;
    }

    public boolean isIDStart() {
        return isIDStart;
    }

    public void setIDStart(boolean IDStart) {
        isIDStart = IDStart;
    }

    public boolean isUUIDStart() {
        return isUUIDStart;
    }

    public void setUUIDStart(boolean UUIDStart) {
        isUUIDStart = UUIDStart;
    }

    public String getUuidValue() {
        return uuidValue;
    }

    public void setUuidValue(String uuidValue) {
        this.uuidValue = uuidValue;
    }
}

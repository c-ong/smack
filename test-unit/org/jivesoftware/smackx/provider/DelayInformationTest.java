/**
 * All rights reserved. Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jivesoftware.smackx.provider;

import static org.junit.Assert.*;

import java.io.IOException;
import java.io.StringReader;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Properties;
import java.util.TimeZone;

import org.jivesoftware.smack.util.StringUtils;
import org.jivesoftware.smack.util.XmlUtil;
import org.jivesoftware.smackx.packet.DelayInfo;
import org.jivesoftware.smackx.packet.DelayInformation;
import org.junit.Test;
import org.w3c.dom.Element;

import com.jamesmurty.utils.XMLBuilder;

public class DelayInformationTest {

    private static Properties outputProperties = new Properties();
    static {
        outputProperties.put(javax.xml.transform.OutputKeys.OMIT_XML_DECLARATION, "yes");
    }

    @Test
    public void delayInformationTest() throws Exception {
        DelayInformationProvider p = new DelayInformationProvider();
        DelayInformation delayInfo;
        String control;
        GregorianCalendar calendar = new GregorianCalendar(2002, 9 - 1, 10, 23, 8, 25);
        calendar.setTimeZone(TimeZone.getTimeZone("UTC"));
        Date date = calendar.getTime(); 
        
        control = XMLBuilder.create("x")
            .a("xmlns", "jabber:x:delay")
            .a("from", "capulet.com")
            .a("stamp", "2002-09-10T23:08:25Z")
            .t("Offline Storage")
            .asString(outputProperties);
        
        Element parser = XmlUtil.getXMLRootNode(control);
        delayInfo = (DelayInformation) p.parseExtension(parser);
        
        assertEquals("capulet.com", delayInfo.getFrom());
        assertEquals(date, delayInfo.getStamp());
        assertEquals("Offline Storage", delayInfo.getReason());

        assertEquals("x", parser.getLocalName());

        control = XMLBuilder.create("x")
            .a("xmlns", "jabber:x:delay")
            .a("from", "capulet.com")
            .a("stamp", "2002-09-10T23:08:25Z")
            .asString(outputProperties);
        
        parser = XmlUtil.getXMLRootNode(control);
        delayInfo = (DelayInformation) p.parseExtension(parser);

        assertEquals("capulet.com", delayInfo.getFrom());
        assertEquals(date, delayInfo.getStamp());
        assertNull(delayInfo.getReason());
        assertEquals("x", parser.getLocalName());
    }

    @Test
    public void delayInfoTest() throws Exception {
        DelayInformationProvider p = new DelayInfoProvider();
        DelayInfo delayInfo;
        String control;
        GregorianCalendar calendar = new GregorianCalendar(2002, 9 - 1, 10, 23, 8, 25);
        calendar.setTimeZone(TimeZone.getTimeZone("UTC"));
        Date date = calendar.getTime(); 
        
        control = XMLBuilder.create("delay")
            .a("xmlns", "urn:xmpp:delay")
            .a("from", "capulet.com")
            .a("stamp", "2002-09-10T23:08:25Z")
            .t("Offline Storage")
            .asString(outputProperties);

        Element parser = XmlUtil.getXMLRootNode(control);
        delayInfo = (DelayInfo) p.parseExtension(parser);
        
        assertEquals("capulet.com", delayInfo.getFrom());
        assertEquals(date, delayInfo.getStamp());
        assertEquals("Offline Storage", delayInfo.getReason());

        assertEquals("delay", parser.getLocalName());
        
        control = XMLBuilder.create("delay")
            .a("xmlns", "urn:xmpp:delay")
            .a("from", "capulet.com")
            .a("stamp", "2002-09-10T23:08:25Z")
            .asString(outputProperties);
        
        parser = XmlUtil.getXMLRootNode(control);
        delayInfo = (DelayInfo) p.parseExtension(parser);
        
        assertEquals("capulet.com", delayInfo.getFrom());
        assertEquals(date, delayInfo.getStamp());
        assertNull(delayInfo.getReason());

        assertEquals("delay", parser.getLocalName());
    }

    @Test
    public void dateFormatsTest() throws Exception {
        DelayInformationProvider p = new DelayInfoProvider();
        DelayInfo delayInfo;
        String control;
        GregorianCalendar calendar = new GregorianCalendar(2002, 9 - 1, 10, 23, 8, 25);
        calendar.setTimeZone(TimeZone.getTimeZone("UTC"));
        
        // XEP-0082 date format
        control = XMLBuilder.create("delay")
            .a("xmlns", "urn:xmpp:delay")
            .a("from", "capulet.com")
            .a("stamp", "2002-09-10T23:08:25.12Z")
            .asString(outputProperties);
        
        delayInfo = (DelayInfo) p.parseExtension(XmlUtil.getXMLRootNode(control));
        
        GregorianCalendar cal = (GregorianCalendar) calendar.clone(); 
        cal.add(Calendar.MILLISECOND, 12);
        assertEquals(cal.getTime(), delayInfo.getStamp());

        // XEP-0082 date format without milliseconds
        control = XMLBuilder.create("delay")
            .a("xmlns", "urn:xmpp:delay")
            .a("from", "capulet.com")
            .a("stamp", "2002-09-10T23:08:25Z")
            .asString(outputProperties);
        
        delayInfo = (DelayInfo) p.parseExtension(XmlUtil.getXMLRootNode(control));
        
        assertEquals(calendar.getTime(), delayInfo.getStamp());

        // XEP-0082 date format without milliseconds and leading 0 in month
        control = XMLBuilder.create("delay")
            .a("xmlns", "urn:xmpp:delay")
            .a("from", "capulet.com")
            .a("stamp", "2002-9-10T23:08:25Z")
            .asString(outputProperties);
        
        delayInfo = (DelayInfo) p.parseExtension(XmlUtil.getXMLRootNode(control));
        
        assertEquals(calendar.getTime(), delayInfo.getStamp());

        // XEP-0091 date format
        control = XMLBuilder.create("delay")
            .a("xmlns", "urn:xmpp:delay")
            .a("from", "capulet.com")
            .a("stamp", "20020910T23:08:25")
            .asString(outputProperties);
        
        delayInfo = (DelayInfo) p.parseExtension(XmlUtil.getXMLRootNode(control));
        
        assertEquals(calendar.getTime(), delayInfo.getStamp());

        // XEP-0091 date format without leading 0 in month
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMd'T'HH:mm:ss");
        dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
        GregorianCalendar dateInPast = new GregorianCalendar();
        if (dateInPast.get(Calendar.MONTH) >= 10) {
            dateInPast.set(Calendar.MONTH, dateInPast.get(Calendar.MONTH) - 3);
        }
        dateInPast.add(Calendar.DAY_OF_MONTH, -3);
        dateInPast.set(Calendar.MILLISECOND, 0);
        
        control = XMLBuilder.create("delay")
            .a("xmlns", "urn:xmpp:delay")
            .a("from", "capulet.com")
            .a("stamp", dateFormat.format(dateInPast.getTime()))
            .asString(outputProperties);
        
        delayInfo = (DelayInfo) p.parseExtension(XmlUtil.getXMLRootNode(control));
        
        assertEquals(dateInPast.getTime(), delayInfo.getStamp());

        // XEP-0091 date format from SMACK-243
        control = XMLBuilder.create("delay")
            .a("xmlns", "urn:xmpp:delay")
            .a("from", "capulet.com")
            .a("stamp", "200868T09:16:20")
            .asString(outputProperties);
        
        delayInfo = (DelayInfo) p.parseExtension(XmlUtil.getXMLRootNode(control));
        Date controlDate = StringUtils.parseXEP0082Date("2008-06-08T09:16:20.0Z");
        
        assertEquals(controlDate, delayInfo.getStamp());

        // invalid date format
        control = XMLBuilder.create("delay")
            .a("xmlns", "urn:xmpp:delay")
            .a("from", "capulet.com")
            .a("stamp", "yesterday")
            .asString(outputProperties);
        
        delayInfo = (DelayInfo) p.parseExtension(XmlUtil.getXMLRootNode(control));
        
        assertNotNull(delayInfo.getStamp());

    }
}

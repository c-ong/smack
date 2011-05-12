/**
 * $Revision: 1456 $
 * $Date: 2005-06-01 22:04:54 -0700 (Wed, 01 Jun 2005) $
 *
 * Copyright 2003-2005 Jive Software.
 *
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

package org.jivesoftware.smack.util;

import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.Vector;

import org.xbill.DNS.Lookup;
import org.xbill.DNS.Record;
import org.xbill.DNS.SRVRecord;
import org.xbill.DNS.TextParseException;
import org.xbill.DNS.Type;

/**
 * Utilty class to perform DNS lookups for XMPP services.
 *
 * @author Matt Tucker
 */
public class DNSUtil {

    /**
     * Create a cache to hold the 100 most recently accessed DNS lookups for a period of
     * 10 minutes.
     */
    private static Map<String, Vector<HostAddress>> cache = new Cache<String, Vector<HostAddress>>(100, 1000 * 60 * 10);

    /**
     * Shuffle a list of items, prioritizing the order by their weight, using a simple
     * PRNG with the given seed.
     * <p>
     * For example, if items is [0, 1] and weightList is [10, 30], the result will
     * be [0,1] 25% of the time and [1,0] 75% of the time.
     * <p>
     * Note that this algorithm is O(n^2), and isn't suitable for very large inputs.
     */
    private static <T> Vector<T> getItemsRandomizedByWeight(Vector<T> items, Vector<Integer> weightList)
    {
        int seed = new Random().nextInt();

        Vector<T> result = new Vector<T>();

        // Make a copy of items and weightList, since we're going to be modifying them.
        items = new Vector<T>(items);
        weightList = new Vector<Integer>(weightList);

        // Shuffle the items first, so items with the same weight are chosen randomly.
        // Reconstruct the PRNG for each shuffle, so the items and weights are kept
        // in sync.
        Collections.shuffle(items, new Random(seed));
        Collections.shuffle(weightList, new Random(seed));

        Random prng = new Random(seed);
        while(!items.isEmpty()) {
            Vector<Integer> cumulativeWeights = new Vector<Integer>(weightList.size());
            int maxSum = 0;
            for(int weight: weightList) {
                // If very large weights would cause us to overflow, clamp all following weights
                // to 0.
                if(maxSum + weight < maxSum)
                    weight = 0;
                maxSum += weight;
                cumulativeWeights.add(maxSum);
            }

            // Choose an item by weight.  Note that we may have items with zero weight,
            // and that nextInt(0) is invalid.
            int weight = 0;
            if(maxSum > 0)
                weight = prng.nextInt(maxSum);

            // Search for the weight we chose.
            int idx = Collections.binarySearch(cumulativeWeights, weight);
            if(idx < 0) {
                // If idx < 0, then -(idx+1) is the first element > weight, which is what we want.
                idx = -(idx+1);
            } else {
                // If idx >= 0, then idx is any element equal to weight.  We want the first value
                // greater than it, so seek forward to find it.  The last weight in cumulativeWeights
                // is always greater than weight, so this is guaranteed to terminate.  The exception
                // is when the list contains only zero weights, in which case we'll use the first
                // item.
                if(maxSum == 0)
                    idx = 0;
                else {
                    while(cumulativeWeights.get(idx) <= weight)
                        ++idx;
                }
            }

            // Add the item we selected to the result.
            result.add(items.get(idx));

            // Remove the item we selected from the source data, and restart.
            items.remove(idx);
            weightList.remove(idx);
        }

        return result;
    }

    private static Vector<HostAddress> resolveSRV(String domain) {
        Vector<SRVRecord> results = new Vector<SRVRecord>();
        try {
            Lookup lookup = new Lookup(domain, Type.SRV);
            Record recs[] = lookup.run();
            if (recs == null)
                    return new Vector<HostAddress>();

            SRVRecord srecs[] = new SRVRecord[recs.length];
            for(int i = 0; i < recs.length; ++i)
                srecs[i] = (SRVRecord) recs[i];

            // Sort the results by ascending priority.
            Arrays.sort(srecs, new Comparator<SRVRecord>() {
                public int compare(SRVRecord lhs, SRVRecord rhs) {
                    return lhs.getPriority() - rhs.getPriority();
                }
            });

            HashMap<Integer, Vector<SRVRecord>> resultsByPriority = new HashMap<Integer, Vector<SRVRecord>>();

            // Separate the results by priority.
            for(int i = 0; i < srecs.length; ++i) {
                SRVRecord srv = srecs[i];
                Vector<SRVRecord> list = resultsByPriority.get(srv.getPriority());
                if(list == null) {
                    list = new Vector<SRVRecord>();
                    resultsByPriority.put(srv.getPriority(), list);
                }
                list.add(srv);
            }

            Vector<Integer> weights = new Vector<Integer>(resultsByPriority.keySet());
            Collections.sort(weights);

            // For each priority group, sort the results based on weight.  Do this
            // in sorted order by weight, so priorities closer to 0 are earlier in
            // the list.
            for(int weight: weights) {
                Vector<SRVRecord> list = resultsByPriority.get(weight);
                Vector<Integer> weightList = new Vector<Integer>();
                for(SRVRecord item: list)
                    weightList.add(item.getWeight());

                Vector<SRVRecord> output = getItemsRandomizedByWeight(list, weightList);
                results.addAll(output);
            }
        } catch (TextParseException e) {
        }

        Vector<HostAddress> addresses = new Vector<HostAddress>();
        for(SRVRecord result: results) {
            // Host entries in DNS should end with a ".".
            String host = result.getTarget().toString();
            if (host.endsWith("."))
                host = host.substring(0, host.length() - 1);
            addresses.add(new HostAddress(host, result.getPort()));
        }
        return addresses;
    }

    /**
     * Returns the host name and port that the specified XMPP server can be
     * reached at for client-to-server communication. A DNS lookup for a SRV
     * record in the form "_xmpp-client._tcp.example.com" is attempted, according
     * to section 14.4 of RFC 3920. If that lookup fails, a lookup in the older form
     * of "_jabber._tcp.example.com" is attempted since servers that implement an
     * older version of the protocol may be listed using that notation. If that
     * lookup fails as well, it's assumed that the XMPP server lives at the
     * host resolved by a DNS lookup at the specified domain on the default port
     * of 5222.<p>
     *
     * As an example, a lookup for "example.com" may return "im.example.com:5269".
     * 
     * Note on SRV record selection.
     * We now check priority and weight, but we still don't do this correctly.
     * The missing behavior is this: if we fail to reach a host based on its SRV
     * record then we need to select another host from the other SRV records.
     * In Smack 3.1.1 we're not going to be able to do the major system redesign to
     * correct this.
     *
     * @param domain the domain.
     * @return a HostAddress, which encompasses the hostname and port that the XMPP
     *      server can be reached at for the specified domain.
     */
    public static Vector<HostAddress> resolveXMPPDomain(String domain) {
        String key = "c" + domain;
        // Return item from cache if it exists.
        if (cache.containsKey(key)) {
            Vector<HostAddress> addresses = cache.get(key);
            if (addresses != null) {
                return addresses;
            }
        }
        Vector<HostAddress> addresses = resolveSRV("_xmpp-client._tcp." + domain);
        if (addresses.isEmpty()) {
            addresses = resolveSRV("_jabber._tcp." + domain);
        }
        if (addresses.isEmpty()) {
            addresses.add(new HostAddress(domain, 5222));
        }
        // Add item to cache.
        cache.put(key, addresses);
        return addresses;
    }

    /**
     * Returns the host name and port that the specified XMPP server can be
     * reached at for server-to-server communication. A DNS lookup for a SRV
     * record in the form "_xmpp-server._tcp.example.com" is attempted, according
     * to section 14.4 of RFC 3920. If that lookup fails, a lookup in the older form
     * of "_jabber._tcp.example.com" is attempted since servers that implement an
     * older version of the protocol may be listed using that notation. If that
     * lookup fails as well, it's assumed that the XMPP server lives at the
     * host resolved by a DNS lookup at the specified domain on the default port
     * of 5269.<p>
     *
     * As an example, a lookup for "example.com" may return "im.example.com:5269".
     *
     * @param domain the domain.
     * @return a HostAddress, which encompasses the hostname and port that the XMPP
     *      server can be reached at for the specified domain.
     */
    public static Vector<HostAddress> resolveXMPPServerDomain(String domain) {
        String key = "s" + domain;
        // Return item from cache if it exists.
        if (cache.containsKey(key)) {
            Vector<HostAddress> addresses = cache.get(key);
            if (addresses != null) {
                return addresses;
            }
        }
        Vector<HostAddress> addresses = resolveSRV("_xmpp-server._tcp." + domain);
        if (addresses.isEmpty()) {
            addresses = resolveSRV("_jabber._tcp." + domain);
        }
        if (addresses.isEmpty()) {
            addresses.add(new HostAddress(domain, 5269));
        }
        // Add item to cache.
        cache.put(key, addresses);
        return addresses;
    }

    /**
     * Encapsulates a hostname and port.
     */
    public static class HostAddress {

        private String host;
        private int port;

        private HostAddress(String host, int port) {
            this.host = host;
            this.port = port;
        }

        /**
         * Returns the hostname.
         *
         * @return the hostname.
         */
        public String getHost() {
            return host;
        }

        /**
         * Returns the port.
         *
         * @return the port.
         */
        public int getPort() {
            return port;
        }

        public String toString() {
            return host + ":" + port;
        }

        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof HostAddress)) {
                return false;
            }

            final HostAddress address = (HostAddress) o;

            if (!host.equals(address.host)) {
                return false;
            }
            return port == address.port;
        }
    }
}
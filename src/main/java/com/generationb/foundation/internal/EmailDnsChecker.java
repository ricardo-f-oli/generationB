package com.generationb.foundation.internal;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.InitialDirContext;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.Locale;

/**
 * Requirement #28: whether the sending domain is actually authenticated.
 *
 * <p>The DNS records themselves cannot be added from here — they belong to whoever runs the
 * domain. What this does is check them, so "is DKIM set up yet?" has an answer on a screen
 * instead of being a question passed between two people who each think the other did it.
 *
 * <p>It is a genuine DNS resolution, not a guess from config. That matters because the failure
 * mode is silent: mail keeps sending with no authentication at all, lands in spam, and nothing in
 * the application looks wrong. The first sign is usually a client saying they never got it.
 *
 * <p>Uses the JDK's own DNS provider rather than a library — this is four record lookups, and
 * a dependency for that would be worse than the twenty lines it saves.
 */
@Slf4j
@Service
public class EmailDnsChecker {

    public enum Status {
        /** Present and correct. */
        PASS,
        /** Present but not right — usually the most dangerous state, because it looks done. */
        WARN,
        /** Absent. */
        FAIL,
        /** DNS did not answer. Says nothing about the record either way. */
        UNKNOWN
    }

    /**
     * @param check    what was tested, in the words the screen shows
     * @param host     the DNS name looked up, so someone can paste it into `dig`
     * @param status   the verdict
     * @param found    what DNS actually returned, truncated
     * @param guidance what to do about it, when the answer is not PASS
     */
    public record Result(String check, String host, Status status, String found, String guidance) {
    }

    public record Report(String sendingDomain, String replyDomain, Status overall,
                         List<Result> results) {
    }

    @Value("${outreach.sendgrid.from-address:noreply@btheagency.com}")
    private String fromAddress;

    @Value("${outreach.sendgrid.reply-domain:reply.btheagency.com}")
    private String replyDomain;

    /**
     * SendGrid's automated security creates two DKIM keys under these names. A domain set up
     * with manual security uses a single custom selector instead, which this reports as a warning
     * rather than a failure — it is not wrong, just not the shape we expect.
     */
    private static final String[] DKIM_SELECTORS = {"s1._domainkey", "s2._domainkey"};

    public Report check() {
        String domain = domainOf(fromAddress);
        List<Result> results = new ArrayList<>();

        results.add(checkSpf(domain));
        results.addAll(checkDkim(domain));
        results.add(checkDmarc(domain));
        results.add(checkInboundMx(replyDomain));

        Status overall = results.stream().anyMatch(r -> r.status() == Status.FAIL) ? Status.FAIL
                : results.stream().anyMatch(r -> r.status() == Status.WARN) ? Status.WARN
                : results.stream().anyMatch(r -> r.status() == Status.UNKNOWN) ? Status.UNKNOWN
                : Status.PASS;

        return new Report(domain, replyDomain, overall, results);
    }

    // =====================================================================

    /**
     * SPF says which servers may send as this domain.
     *
     * <p>Two failure modes worth separating. No record at all is obvious. <em>Two</em> records is
     * the subtle one: RFC 7208 says a domain publishing more than one SPF record is a permanent
     * error, so every check fails — and it happens the moment somebody adds a second one for a
     * different service rather than merging the includes.
     */
    private Result checkSpf(String domain) {
        List<String> txt = lookup(domain, "TXT");
        if (txt == null) {
            return unknown("SPF", domain);
        }

        List<String> spf = txt.stream()
                .map(EmailDnsChecker::unquote)
                .filter(value -> value.toLowerCase(Locale.UK).startsWith("v=spf1"))
                .toList();

        if (spf.isEmpty()) {
            return new Result("SPF", domain, Status.FAIL, "no v=spf1 record",
                    "Add a TXT record: v=spf1 include:sendgrid.net ~all");
        }
        if (spf.size() > 1) {
            return new Result("SPF", domain, Status.WARN, spf.size() + " SPF records",
                    "A domain may publish only one SPF record — two is a permanent error and "
                            + "every check fails. Merge them into a single record.");
        }

        String record = spf.get(0);
        if (!record.toLowerCase(Locale.UK).contains("include:sendgrid.net")) {
            return new Result("SPF", domain, Status.WARN, record,
                    "The record exists but does not authorise SendGrid. Add "
                            + "include:sendgrid.net to it.");
        }
        if (record.toLowerCase(Locale.UK).contains("+all")) {
            return new Result("SPF", domain, Status.WARN, record,
                    "+all authorises the entire internet to send as this domain. Use ~all.");
        }
        return new Result("SPF", domain, Status.PASS, record, null);
    }

    /**
     * DKIM signs each message so the receiver can prove it was not altered and did come from
     * this domain. SendGrid publishes the keys as CNAMEs pointing back at their infrastructure.
     */
    private List<Result> checkDkim(String domain) {
        List<Result> results = new ArrayList<>();
        int resolved = 0;

        for (String selector : DKIM_SELECTORS) {
            String host = selector + "." + domain;
            List<String> cname = lookup(host, "CNAME");
            if (cname == null) {
                results.add(unknown("DKIM " + selector, host));
                continue;
            }
            if (cname.isEmpty()) {
                results.add(new Result("DKIM " + selector, host, Status.FAIL, "not found",
                        "Add the CNAME that SendGrid gives you under Settings → Sender "
                                + "Authentication → Authenticate Your Domain."));
                continue;
            }
            String target = cname.get(0);
            if (!target.toLowerCase(Locale.UK).contains("sendgrid.net")) {
                results.add(new Result("DKIM " + selector, host, Status.WARN, target,
                        "Resolves, but not to SendGrid. Check the value matches what SendGrid "
                                + "issued for this domain."));
                continue;
            }
            resolved++;
            results.add(new Result("DKIM " + selector, host, Status.PASS, target, null));
        }

        // One of two is worse than neither: it means somebody started and stopped.
        if (resolved == 1) {
            results.add(new Result("DKIM pair", domain, Status.WARN, "1 of 2 selectors resolve",
                    "SendGrid's automated security issues two keys and rotates between them. "
                            + "With only one in place, half the mail fails DKIM."));
        }
        return results;
    }

    /**
     * DMARC tells receivers what to do when SPF and DKIM fail, and is where the reports come
     * from. Gmail and Yahoo have required it for bulk senders since 2024, so its absence is a
     * deliverability problem on its own rather than a nice-to-have.
     */
    private Result checkDmarc(String domain) {
        String host = "_dmarc." + domain;
        List<String> txt = lookup(host, "TXT");
        if (txt == null) {
            return unknown("DMARC", host);
        }

        String record = txt.stream()
                .map(EmailDnsChecker::unquote)
                .filter(value -> value.toLowerCase(Locale.UK).startsWith("v=dmarc1"))
                .findFirst()
                .orElse(null);

        if (record == null) {
            return new Result("DMARC", host, Status.FAIL, "not found",
                    "Add a TXT record: v=DMARC1; p=none; rua=mailto:dmarc@" + domain
                            + " — start at p=none so nothing is rejected while you watch the "
                            + "reports, then tighten to quarantine.");
        }
        if (!record.toLowerCase(Locale.UK).contains("rua=")) {
            return new Result("DMARC", host, Status.WARN, record,
                    "No rua= address, so nobody receives the aggregate reports. Without them "
                            + "you cannot tell whether tightening the policy is safe.");
        }
        return new Result("DMARC", host, Status.PASS, record, null);
    }

    /**
     * Requirement #30: inbound replies need an MX record pointing at SendGrid's Inbound Parse,
     * on the subdomain the Reply-To addresses are built from.
     */
    private Result checkInboundMx(String domain) {
        List<String> mx = lookup(domain, "MX");
        if (mx == null) {
            return unknown("Inbound MX", domain);
        }
        if (mx.isEmpty()) {
            return new Result("Inbound MX", domain, Status.FAIL, "not found",
                    "Add an MX record on " + domain + " pointing at mx.sendgrid.net "
                            + "(priority 10), then configure Inbound Parse for this host.");
        }
        String record = mx.get(0);
        if (!record.toLowerCase(Locale.UK).contains("sendgrid.net")) {
            return new Result("Inbound MX", domain, Status.WARN, record,
                    "Mail for this subdomain goes somewhere other than SendGrid, so replies "
                            + "will not reach the platform.");
        }
        return new Result("Inbound MX", domain, Status.PASS, record, null);
    }

    // =====================================================================

    /**
     * @return the record values, an empty list when the name resolves but has no such record,
     *         or null when DNS could not be reached at all — which is a different thing and must
     *         not be reported as a missing record.
     */
    private List<String> lookup(String host, String type) {
        Hashtable<String, String> env = new Hashtable<>();
        env.put("java.naming.factory.initial", "com.sun.jndi.dns.DnsContextFactory");
        // A settings screen must not hang on a slow resolver.
        env.put("com.sun.jndi.dns.timeout.initial", "2000");
        env.put("com.sun.jndi.dns.timeout.retries", "2");

        InitialDirContext context = null;
        try {
            context = new InitialDirContext(env);
            Attributes attributes = context.getAttributes(host, new String[] {type});
            Attribute attribute = attributes.get(type);
            if (attribute == null) {
                return List.of();
            }
            List<String> values = new ArrayList<>();
            for (int i = 0; i < attribute.size(); i++) {
                values.add(String.valueOf(attribute.get(i)));
            }
            return values;
        } catch (javax.naming.NameNotFoundException e) {
            // The name does not exist. That is a missing record, not a resolver failure.
            return List.of();
        } catch (NamingException e) {
            log.debug("DNS lookup failed for {} {}: {}", type, host, e.getClass().getSimpleName());
            return null;
        } finally {
            if (context != null) {
                try {
                    context.close();
                } catch (NamingException ignored) {
                    // Closing a DNS context is best-effort.
                }
            }
        }
    }

    private static Result unknown(String check, String host) {
        return new Result(check, host, Status.UNKNOWN, "DNS did not answer",
                "Could not reach a resolver. This says nothing about whether the record "
                        + "exists — try again, or check from a machine with outbound DNS.");
    }

    /** TXT values arrive quoted, and long ones arrive split into several quoted chunks. */
    private static String unquote(String value) {
        return value.replace("\" \"", "").replace("\"", "").trim();
    }

    private static String domainOf(String address) {
        if (address == null || !address.contains("@")) {
            return address == null ? "" : address;
        }
        return address.substring(address.indexOf('@') + 1).trim().toLowerCase(Locale.UK);
    }
}

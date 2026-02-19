package com.performativ.example.tools;

import net.datafaker.Faker;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDate;
import java.util.Locale;

public class ClientCsvGenerator {
    private static String esc(String s) {
        if (s == null) return "";
        boolean needsQuotes = s.contains(",") || s.contains("\"") || s.contains("\n") || s.contains("\r");
        s = s.replace("\"", "\"\"");
        return needsQuotes ? "\"" + s + "\"" : s;
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("Usage: ClientCsvGenerator <rows> <outputCsvPath>");
            System.exit(2);
        }
        long rows = Long.parseLong(args[0]);
        Path out = Paths.get(args[1]);
        Files.createDirectories(out.getParent());

        Faker faker = new Faker(new Locale("en"));
        try (BufferedWriter w = Files.newBufferedWriter(out, StandardCharsets.UTF_8)) {
            // header
            w.write(String.join(",",
                    "client_id","first_name","last_name","email","phone",
                    "address","city","state","zip","country",
                    "dob","created_at","status","segment","notes",
                    "account_no","tax_id","currency","language","advisor"));
            w.write("\n");

            for (long i = 1; i <= rows; i++) {
                String line = String.join(",",
                        esc("C" + i),
                        esc(faker.name().firstName()),
                        esc(faker.name().lastName()),
                        esc(faker.internet().emailAddress()),
                        esc(faker.phoneNumber().phoneNumber()),
                        esc(faker.address().streetAddress()),
                        esc(faker.address().city()),
                        esc(faker.address().stateAbbr()),
                        esc(faker.address().zipCode()),
                        esc(faker.address().countryCode()),
                        esc(faker.date().birthday(18, 90).toLocalDateTime().toLocalDate().toString()),
                        esc(LocalDate.now().toString()),
                        esc(faker.options().option("active","prospect","blocked")),
                        esc(faker.options().option("retail","affluent","private")),
                        esc(faker.lorem().sentence()),
                        esc(faker.numerify("##########")),
                        esc(faker.numerify("#########")),
                        esc(faker.options().option("USD","GBP","EUR","NOK","SEK")),
                        esc(faker.options().option("en","de","fr","es","no")),
                        esc(faker.name().fullName())
                );
                w.write(line);
                w.write("\n");
            }
        }
        System.out.println("Wrote CSV: " + out);
    }
}

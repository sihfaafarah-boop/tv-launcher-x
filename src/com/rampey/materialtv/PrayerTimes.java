package com.rampey.materialtv;

import java.util.Calendar;
import java.util.TimeZone;

/**
 * Jadwal sholat yang dihitung langsung di TV dengan rumus astronomi (tanpa internet).
 * Kriteria Kemenag RI: Subuh 20 derajat, Isya 18 derajat, Ashar bayangan 1x (Syafi'i),
 * plus ihtiyath (tambahan pengaman) beberapa menit.
 */
final class PrayerTimes {

    // ---- Ubah di sini kalau pindah kota ----
    static final String CITY = "Tangerang Selatan";
    static final double LATITUDE = -6.2886;     // lintang (selatan = minus)
    static final double LONGITUDE = 106.7179;   // bujur
    static final int IHTIYATH_MINUTES = 2;      // tambahan menit pengaman ala Kemenag

    static final String[] NAMES = {"Subuh", "Dzuhur", "Ashar", "Maghrib", "Isya"};

    private static final double FAJR_ANGLE = 20.0;
    private static final double ISHA_ANGLE = 18.0;
    private static final double SUNSET_ANGLE = 0.833;   // refraksi + jari-jari matahari

    private PrayerTimes() {
    }

    /** Lima waktu sholat untuk tanggal pada {@code day}, dalam menit sejak tengah malam waktu lokal TV. */
    static int[] minutesFor(Calendar day) {
        int y = day.get(Calendar.YEAR);
        int m = day.get(Calendar.MONTH) + 1;
        int d = day.get(Calendar.DAY_OF_MONTH);
        Calendar noon = (Calendar) day.clone();
        noon.set(Calendar.HOUR_OF_DAY, 12);
        noon.set(Calendar.MINUTE, 0);
        double zone = TimeZone.getDefault().getOffset(noon.getTimeInMillis()) / 3_600_000.0;

        double jd = julian(y, m, d) - LONGITUDE / (15.0 * 24.0);

        double dhuhr = midDay(jd, 12.0 / 24.0);
        double fajr = midDay(jd, 5.0 / 24.0) - hourAngle(jd, 5.0 / 24.0, FAJR_ANGLE);
        double asr = midDay(jd, 13.0 / 24.0) + asrHourAngle(jd, 13.0 / 24.0, 1);
        double maghrib = midDay(jd, 18.0 / 24.0) + hourAngle(jd, 18.0 / 24.0, SUNSET_ANGLE);
        double isha = midDay(jd, 18.0 / 24.0) + hourAngle(jd, 18.0 / 24.0, ISHA_ANGLE);

        double shift = zone - LONGITUDE / 15.0;
        double[] hours = {fajr + shift, dhuhr + shift, asr + shift, maghrib + shift, isha + shift};
        int[] out = new int[5];
        for (int i = 0; i < 5; i++) {
            out[i] = (int) Math.round(hours[i] * 60.0) + IHTIYATH_MINUTES;
            out[i] = ((out[i] % 1440) + 1440) % 1440;
        }
        return out;
    }

    /** Teks "HH:mm" dari menit sejak tengah malam. */
    static String format(int minutes) {
        return String.format(java.util.Locale.US, "%02d:%02d", minutes / 60, minutes % 60);
    }

    // ---- astronomi ----

    private static double julian(int year, int month, int day) {
        if (month <= 2) {
            year -= 1;
            month += 12;
        }
        double a = Math.floor(year / 100.0);
        double b = 2 - a + Math.floor(a / 4.0);
        return Math.floor(365.25 * (year + 4716)) + Math.floor(30.6001 * (month + 1)) + day + b - 1524.5;
    }

    /** Deklinasi matahari (derajat) dan persamaan waktu (jam) pada hari Julian jd. */
    private static double[] sun(double jd) {
        double dd = jd - 2451545.0;
        double g = fix(357.529 + 0.98560028 * dd, 360);
        double q = fix(280.459 + 0.98564736 * dd, 360);
        double l = fix(q + 1.915 * sin(g) + 0.020 * sin(2 * g), 360);
        double e = 23.439 - 0.00000036 * dd;
        double ra = atan2(cos(e) * sin(l), cos(l)) / 15.0;
        ra = fix(ra, 24);
        double eqt = q / 15.0 - ra;
        double decl = asin(sin(e) * sin(l));
        return new double[]{decl, eqt};
    }

    /** Tengah hari matahari (jam, waktu matahari lokal) untuk perkiraan waktu t (pecahan hari). */
    private static double midDay(double jd, double t) {
        double eqt = sun(jd + t)[1];
        return fix(12 - eqt, 24);
    }

    /** Selisih jam dari tengah hari saat matahari berada {@code angle} derajat di bawah ufuk. */
    private static double hourAngle(double jd, double t, double angle) {
        double decl = sun(jd + t)[0];
        double cosH = (-sin(angle) - sin(decl) * sin(LATITUDE)) / (cos(decl) * cos(LATITUDE));
        cosH = Math.max(-1, Math.min(1, cosH));
        return acos(cosH) / 15.0;
    }

    /** Selisih jam dari tengah hari untuk Ashar (bayangan = factor x tinggi benda + bayangan siang). */
    private static double asrHourAngle(double jd, double t, int factor) {
        double decl = sun(jd + t)[0];
        double alt = -Math.toDegrees(Math.atan(1.0 / (factor + Math.tan(Math.toRadians(Math.abs(LATITUDE - decl))))));
        return hourAngle(jd, t, alt);
    }

    private static double sin(double deg) {
        return Math.sin(Math.toRadians(deg));
    }

    private static double cos(double deg) {
        return Math.cos(Math.toRadians(deg));
    }

    private static double asin(double x) {
        return Math.toDegrees(Math.asin(x));
    }

    private static double acos(double x) {
        return Math.toDegrees(Math.acos(x));
    }

    private static double atan2(double y, double x) {
        return Math.toDegrees(Math.atan2(y, x));
    }

    private static double fix(double a, double b) {
        a = a - b * Math.floor(a / b);
        return a < 0 ? a + b : a;
    }
}

package com.example.bbettercalendar.social.stats;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Ranking de la semana (02 §6, D-030; F3-u3). Puro: sin Android ni reloj, las claves de hoy entran por
 * {@link WeekKey.Keys}. Se comparan claves, nunca horas.
 *
 * <ul>
 *   <li>Los minutos de la semana y los días solo cuentan si la {@code weekKey} del amigo es la mía; los
 *       minutos de hoy, solo si su {@code dayKey} es la mía; si no, 0. Quien no tiene documento cuenta 0 y
 *       sigue apareciendo.</li>
 *   <li>Minutos ordena por {@code minutesWeekCapped} (descendente). Constancia, por la razón SIN tope
 *       {@code días / objetivo} (D-030, decisión d3: 5/3 gana a 3/3). Los empates comparten puesto con
 *       competición estándar (1, 2, 2, 4); dentro del empate, yo primero y luego por {@code @usuario}.</li>
 *   <li>Corona: el amigo (nunca yo) con más {@code min(minutesToday, 240)} entre quienes tienen mi
 *       {@code dayKey}; ninguna con 0; empate por {@code @usuario} ascendente.</li>
 *   <li>Con amigos pero sin mi documento, yo cuento 0.</li>
 * </ul>
 */
public final class RankingCalculator {

    /** Tope diario de minutos para la corona (D-005). */
    public static final int DAILY_CAP = 240;

    private RankingCalculator() {
    }

    /** Un amigo de entrada: uid, {@code @usuario} y sus stats (null si no tiene documento). */
    public static final class FriendInput {
        @NonNull
        public final String uid;
        @NonNull
        public final String handle;
        @Nullable
        public final FriendStats stats;

        public FriendInput(@NonNull String uid, @NonNull String handle, @Nullable FriendStats stats) {
            this.uid = uid;
            this.handle = handle;
            this.stats = stats;
        }
    }

    /**
     * Las stats de alguien vistas desde mis claves: lo que no es de esta semana o de hoy pasa a 0 y la racha
     * semanal solo se muestra si es de esta semana o de la anterior (las semanas intermedias no se
     * cumplieron). {@code bestWeekStreak} y {@code totalMinutes} tal como se publicaron. Null si no hay stats.
     */
    @Nullable
    public static FriendStats applySemantics(@Nullable FriendStats s, @NonNull WeekKey.Keys keys) {
        if (s == null) {
            return null;
        }
        boolean sameWeek = s.weekKey.equals(keys.week);
        boolean sameDay = s.dayKey.equals(keys.day);
        boolean streakAlive = sameWeek || s.weekKey.equals(keys.previousWeek);
        return new FriendStats(s.dayKey, s.weekKey, sameDay ? s.minutesToday : 0,
                sameWeek ? s.minutesWeekCapped : 0, sameWeek ? s.daysThisWeek : 0, s.weeklyGoal,
                streakAlive ? s.weekStreak : 0, s.bestWeekStreak, s.totalMinutes, s.updatedAtMillis);
    }

    @NonNull
    public static Ranking compute(@NonNull String myUid, @Nullable FriendStats mine,
                                  @NonNull List<FriendInput> friends, @NonNull WeekKey.Keys keys) {
        List<Entry> all = new ArrayList<>();
        all.add(new Entry(myUid, null, true, applySemantics(mine, keys)));
        for (FriendInput f : friends) {
            all.add(new Entry(f.uid, f.handle, false, applySemantics(f.stats, keys)));
        }

        List<Ranking.Row> minutes = rank(all, new Comparator<Entry>() {
            @Override
            public int compare(Entry a, Entry b) {
                return Integer.compare(b.week(), a.week());
            }
        }, true);
        List<Ranking.Row> consistency = rank(all, new Comparator<Entry>() {
            @Override
            public int compare(Entry a, Entry b) {
                // días/objetivo descendente sin dividir: da/ga > db/gb <=> da*gb > db*ga.
                // Sin stats (objetivo 0) cuenta como 0/1.
                long da = a.goal() == 0 ? 0 : a.days();
                long ga = a.goal() == 0 ? 1 : a.goal();
                long db = b.goal() == 0 ? 0 : b.days();
                long gb = b.goal() == 0 ? 1 : b.goal();
                long left = db * ga;
                long right = da * gb;
                return Long.compare(left, right);
            }
        }, false);

        int myPosition = Ranking.NO_RANK;
        if (!friends.isEmpty()) {
            for (Ranking.Row r : minutes) {
                if (r.isMe) {
                    myPosition = r.position;
                }
            }
        }
        return new Ranking(minutes, consistency, crown(all, keys), myPosition, all.size());
    }

    private static final class Entry {
        final String uid;
        final String handle;
        final boolean me;
        final FriendStats s; // ya con la semántica aplicada

        Entry(String uid, String handle, boolean me, FriendStats s) {
            this.uid = uid;
            this.handle = handle;
            this.me = me;
            this.s = s;
        }

        int week() {
            return s == null ? 0 : s.minutesWeekCapped;
        }

        int days() {
            return s == null ? 0 : s.daysThisWeek;
        }

        int goal() {
            return s == null ? 0 : s.weeklyGoal;
        }
    }

    private static List<Ranking.Row> rank(List<Entry> all, final Comparator<Entry> byValueDesc,
                                          boolean minutesCategory) {
        List<Entry> sorted = new ArrayList<>(all);
        Collections.sort(sorted, new Comparator<Entry>() {
            @Override
            public int compare(Entry a, Entry b) {
                int c = byValueDesc.compare(a, b);
                if (c != 0) {
                    return c;
                }
                if (a.me != b.me) {
                    return a.me ? -1 : 1;
                }
                String ha = a.handle == null ? "" : a.handle;
                String hb = b.handle == null ? "" : b.handle;
                c = ha.compareTo(hb);
                return c != 0 ? c : a.uid.compareTo(b.uid);
            }
        });
        List<Ranking.Row> rows = new ArrayList<>();
        int position = 0;
        for (int i = 0; i < sorted.size(); i++) {
            Entry e = sorted.get(i);
            if (i == 0 || byValueDesc.compare(sorted.get(i - 1), e) != 0) {
                position = i + 1;
            }
            rows.add(new Ranking.Row(e.uid, e.handle, position, minutesCategory ? e.week() : e.days(),
                    e.goal(), e.me));
        }
        return rows;
    }

    @Nullable
    private static String crown(List<Entry> all, WeekKey.Keys keys) {
        Entry best = null;
        int bestMinutes = 0;
        for (Entry e : all) {
            if (e.me || e.s == null || !e.s.dayKey.equals(keys.day)) {
                continue;
            }
            int m = Math.min(e.s.minutesToday, DAILY_CAP);
            if (m > bestMinutes || (m == bestMinutes && best != null
                    && e.handle.compareTo(best.handle) < 0)) {
                best = e;
                bestMinutes = m;
            }
        }
        return bestMinutes > 0 && best != null ? best.uid : null;
    }
}

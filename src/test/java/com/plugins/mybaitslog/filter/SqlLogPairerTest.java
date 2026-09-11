package com.plugins.mybaitslog.filter;

import com.plugins.mybaitslog.util.KeyNameUtil;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class SqlLogPairerTest {

    private static final String THREAD_4_PREPARING = "2026-09-12T00:55:28.805+08:00 DEBUG 33091 --- [rice-trade] [nio-8848-exec-4] c.r.t.m.S.findRoleCodesByUserId : ==>  Preparing: select r.code from system_roles r where r.user_id = ?";
    private static final String THREAD_4_PARAMETERS = "2026-09-12T00:55:28.805+08:00 DEBUG 33091 --- [rice-trade] [nio-8848-exec-4] c.r.t.m.S.findRoleCodesByUserId : ==> Parameters: 1(Long)";
    private static final String THREAD_5_PREPARING = "2026-09-12T00:55:28.806+08:00 DEBUG 33091 --- [rice-trade] [nio-8848-exec-5] c.r.t.m.U.findUserName : ==>  Preparing: select u.name from system_users u where u.id = ?";
    private static final String THREAD_5_PARAMETERS = "2026-09-12T00:55:28.807+08:00 DEBUG 33091 --- [rice-trade] [nio-8848-exec-5] c.r.t.m.U.findUserName : ==> Parameters: 2(Long)";

    @Test
    public void shouldExtractRequestThreadInsteadOfApplicationName() {
        SqlLogPairer pairer = new SqlLogPairer();

        assertEquals("nio-8848-exec-4", pairer.extractThreadKey(THREAD_4_PREPARING));
    }

    @Test
    public void shouldPairInterleavedSqlByThread() {
        SqlLogPairer pairer = new SqlLogPairer();

        assertNull(pairer.accept(THREAD_4_PREPARING, KeyNameUtil.PREPARING, KeyNameUtil.PARAMETERS));
        assertNull(pairer.accept(THREAD_5_PREPARING, KeyNameUtil.PREPARING, KeyNameUtil.PARAMETERS));

        SqlLogPairer.SqlLogPair thread5Pair = pairer.accept(THREAD_5_PARAMETERS, KeyNameUtil.PREPARING, KeyNameUtil.PARAMETERS);
        SqlLogPairer.SqlLogPair thread4Pair = pairer.accept(THREAD_4_PARAMETERS, KeyNameUtil.PREPARING, KeyNameUtil.PARAMETERS);

        assertNotNull(thread5Pair);
        assertEquals(THREAD_5_PREPARING, thread5Pair.getPreparingLine());
        assertEquals(THREAD_5_PARAMETERS, thread5Pair.getParametersLine());
        assertNotNull(thread4Pair);
        assertEquals(THREAD_4_PREPARING, thread4Pair.getPreparingLine());
        assertEquals(THREAD_4_PARAMETERS, thread4Pair.getParametersLine());
    }

    @Test
    public void shouldNotUseAnotherThreadsPreparing() {
        SqlLogPairer pairer = new SqlLogPairer();

        pairer.accept(THREAD_4_PREPARING, KeyNameUtil.PREPARING, KeyNameUtil.PARAMETERS);

        assertNull(pairer.accept(THREAD_5_PARAMETERS, KeyNameUtil.PREPARING, KeyNameUtil.PARAMETERS));
        assertNotNull(pairer.accept(THREAD_4_PARAMETERS, KeyNameUtil.PREPARING, KeyNameUtil.PARAMETERS));
    }

    @Test
    public void shouldPairConcurrentInputByThread() throws Exception {
        SqlLogPairer pairer = new SqlLogPairer();
        CountDownLatch bothPreparingReceived = new CountDownLatch(2);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<SqlLogPairer.SqlLogPair> thread4 = executor.submit(() -> pairAfterBothPreparing(pairer,
                    THREAD_4_PREPARING, THREAD_4_PARAMETERS, bothPreparingReceived));
            Future<SqlLogPairer.SqlLogPair> thread5 = executor.submit(() -> pairAfterBothPreparing(pairer,
                    THREAD_5_PREPARING, THREAD_5_PARAMETERS, bothPreparingReceived));

            assertEquals(THREAD_4_PREPARING, thread4.get(2, TimeUnit.SECONDS).getPreparingLine());
            assertEquals(THREAD_5_PREPARING, thread5.get(2, TimeUnit.SECONDS).getPreparingLine());
        } finally {
            executor.shutdownNow();
        }
    }

    private SqlLogPairer.SqlLogPair pairAfterBothPreparing(SqlLogPairer pairer, String preparing,
                                                            String parameters, CountDownLatch bothPreparingReceived) throws InterruptedException {
        pairer.accept(preparing, KeyNameUtil.PREPARING, KeyNameUtil.PARAMETERS);
        bothPreparingReceived.countDown();
        assertTrue(bothPreparingReceived.await(2, TimeUnit.SECONDS));
        return pairer.accept(parameters, KeyNameUtil.PREPARING, KeyNameUtil.PARAMETERS);
    }
}

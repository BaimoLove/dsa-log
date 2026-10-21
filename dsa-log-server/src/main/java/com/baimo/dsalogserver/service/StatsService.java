package com.baimo.dsalogserver.service;

import com.baimo.dsalogserver.vo.OverviewVO;

/** 概览指标。三个「今天」的口径见 {@link com.baimo.dsalogserver.vo.OverviewVO}。 */
public interface StatsService {

    OverviewVO overview();
}

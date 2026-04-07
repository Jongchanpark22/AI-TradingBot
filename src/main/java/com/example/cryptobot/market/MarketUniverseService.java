package com.example.cryptobot.market;

import com.example.cryptobot.upbit.client.UpbitApiClient;
import com.example.cryptobot.upbit.dto.UpbitTickerDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class MarketUniverseService {

    private final UpbitApiClient upbitApiClient;

    /**
     * 가장 대중적인 코인 5개 선정
     * 기준: 24h 거래대금/거래량 기반
     */
    public List<String> getTopTradableMarkets(int count) {
        String markets = "KRW-BTC,KRW-ETH,KRW-XRP,KRW-SOL,KRW-DOGE,KRW-ADA,KRW-AVAX,KRW-LINK,KRW-DOT,KRW-TRX";

        List<UpbitTickerDto> tickers = upbitApiClient.getTickers(markets);

        List<String> result = tickers.stream()
                .sorted(Comparator.comparing(this::calcTradingPower).reversed())
                .limit(count)
                .map(UpbitTickerDto::getMarket)
                .toList();

        log.info("[유니버스 선정] topMarkets={}", result);
        return result;
    }

    private BigDecimal calcTradingPower(UpbitTickerDto ticker) {
        BigDecimal accTradePrice24h = safe(ticker.getAccTradePrice24h());
        BigDecimal accTradeVolume24h = safe(ticker.getAccTradeVolume24h());

        return accTradePrice24h.add(accTradeVolume24h.multiply(new BigDecimal("1000")));
    }

    private BigDecimal safe(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }
}
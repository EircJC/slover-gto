package com.solvergto.trainer;

import com.solvergto.poker.Card;
import com.solvergto.poker.Combo;
import com.solvergto.trainer.TrainerModels.ActionOptionView;
import com.solvergto.trainer.TrainerModels.ActionRequest;
import com.solvergto.trainer.TrainerModels.ActionResponse;
import com.solvergto.trainer.TrainerModels.ConfigSummaryView;
import com.solvergto.trainer.TrainerModels.DecisionMode;
import com.solvergto.trainer.TrainerModels.DrawType;
import com.solvergto.trainer.TrainerModels.FeedbackView;
import com.solvergto.trainer.TrainerModels.HandHistoryView;
import com.solvergto.trainer.TrainerModels.HandRange;
import com.solvergto.trainer.TrainerModels.HandType;
import com.solvergto.trainer.TrainerModels.OperationView;
import com.solvergto.trainer.TrainerModels.RelativePosition;
import com.solvergto.trainer.TrainerModels.ScoreBand;
import com.solvergto.trainer.TrainerModels.ScenarioType;
import com.solvergto.trainer.TrainerModels.SeatPosition;
import com.solvergto.trainer.TrainerModels.SeatView;
import com.solvergto.trainer.TrainerModels.SessionView;
import com.solvergto.trainer.TrainerModels.SpotView;
import com.solvergto.trainer.TrainerModels.Stage;
import com.solvergto.trainer.TrainerModels.StartRequest;
import com.solvergto.trainer.TrainerModels.StartResponse;
import com.solvergto.trainer.TrainerModels.StatsView;
import com.solvergto.service.TrainerPersistenceService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class TrainerDemoService {
    private static final List<SeatPosition> SEAT_ORDER = List.of(
            SeatPosition.UTG, SeatPosition.HJ, SeatPosition.CO, SeatPosition.BTN, SeatPosition.SB, SeatPosition.BB
    );
    private static final List<String> PRE_FLOP_PREMIUMS = List.of("AsAh", "KdKc", "AdKh", "QsQd", "AcQc", "JhJc");
    private static final List<String> POCKET_PAIRS = List.of("TcTd", "9s9d", "8h8c", "7d7c", "6s6h", "5c5d");
    private static final List<String> SUITED_BROADWAYS = List.of("AsQs", "KhQh", "QdJd", "JhTh", "AcJc", "KsTs");
    private static final List<String> OFFSUIT_BROADWAYS = List.of("AdQh", "KcQd", "AhJd", "KsJc", "QcTd", "KdTc");
    private static final List<String> SUITED_CONNECTORS = List.of("9h8h", "8s7s", "7d6d", "6c5c", "Ts9s", "5h4h");
    private static final List<String> AX_COMBOS = List.of("As5s", "Ah4d", "Ac9c", "AdTd", "Ah7h", "AsJc");
    private static final List<String> BLUFF_CATCHERS = List.of("Ad8c", "Kh9d", "Qh9c", "Jc8d", "Tc7h", "9s8c");
    private static final List<String> MONSTER_POST = List.of("AsKs", "QhQs", "JhTh", "9c9d", "AcQc", "KdQd");
    private static final List<String> DRY_RIVER_BOARDS = List.of("Ah7d2c9s4h", "Kd8c3sTs5d", "Qs7h4c2dJc", "Jh6s2d9c4c");
    private static final List<String> FLUSH_DRAW_FLOPS = List.of("As7s2d", "Kh9h4c", "Qc8c3d", "Jd7d2s");
    private static final List<String> STRAIGHT_DRAW_FLOPS = List.of("Ts8d4c", "9h7c2d", "Jc9s3h", "8d6c2h");
    private static final List<String> COMBO_DRAW_FLOPS = List.of("QsJs3d", "Th8h2c", "9d7d4s", "KcQc5h");
    private static final List<String> TURN_BOARDS = List.of("As7s2dTc", "Kh9h4cJd", "Qc8c3dTs", "Th8h2c7d");
    private static final List<String> RIVER_DYNAMIC_BOARDS = List.of("As7s2dTc4s", "Kh9h4cJd2h", "Qc8c3dTs9s", "Th8h2c7d5h");

    private final Map<String, SessionState> sessions = new ConcurrentHashMap<>();
    private final TrainerPersistenceService trainerPersistenceService;

    public TrainerDemoService(TrainerPersistenceService trainerPersistenceService) {
        this.trainerPersistenceService = trainerPersistenceService;
    }

    public StartResponse start(long playerId, String mode, StartRequest request) {
        TrainingConfig config = toConfig(request);
        Random random = new Random();
        List<SpotState> spots = new ArrayList<>();
        for (int i = 0; i < config.sessionHands(); i++) {
            spots.addAll(generateHandSpots(config, i + 1, random));
        }

        String sessionId = UUID.randomUUID().toString();
        SessionState state = new SessionState(sessionId, config, spots);
        sessions.put(sessionId, state);
        SessionView session = toSessionView(state);
        trainerPersistenceService.saveNewSession(playerId, mode, session, request);
        return new StartResponse(sessionId, session);
    }

    public ActionResponse act(long playerId, String mode, String sessionId, ActionRequest request) {
        SessionState state = sessions.get(sessionId);
        if (state == null) {
            throw new IllegalArgumentException("Trainer session not found or expired");
        }
        if (state.completed()) {
            throw new IllegalArgumentException("Trainer session is already completed");
        }
        if (state.awaitingNextHand()) {
            throw new IllegalArgumentException("Please click next hand before submitting another action");
        }
        SpotState spot = state.currentSpot();
        ActionProfile profile = spot.profileByCode().get(request.actionCode());
        if (profile == null) {
            throw new IllegalArgumentException("Unsupported action for current spot: " + request.actionCode());
        }

        state.recordAutomaticOperations(spot);
        ScoreBand band = classify(profile, spot.bestProfile());
        FeedbackView feedback = buildFeedback(spot, profile, band);
        state.recordHeroOperation(spot, profile, band, feedback);
        boolean handCompleted = shouldCompleteHand(spot, profile);
        state.stats().apply(band, handCompleted);
        if (isAllInAction(profile)) {
            state.completeHandByAllIn(spot);
            state.advancePastHand(spot.handNumber());
        } else if (isFoldAction(profile)) {
            state.completeHandByFold(spot);
            state.advancePastHand(spot.handNumber());
        } else if (spot.stage() == Stage.RIVER) {
            state.completeHandByShowdown(spot);
            state.advancePastHand(spot.handNumber());
        } else {
            state.advance();
        }
        if (handCompleted && !state.completed()) {
            state.pauseForNextHand();
        }
        SessionView session = toSessionView(state);
        trainerPersistenceService.saveSnapshot(playerId, mode, session);
        return new ActionResponse(session, feedback);
    }

    public SessionView nextHand(long playerId, String mode, String sessionId) {
        SessionState state = sessions.get(sessionId);
        if (state == null) {
            throw new IllegalArgumentException("Trainer session not found or expired");
        }
        if (state.completed()) {
            throw new IllegalArgumentException("Trainer session is already completed");
        }
        state.continueToNextHand();
        SessionView session = toSessionView(state);
        trainerPersistenceService.saveSnapshot(playerId, mode, session);
        return session;
    }

    private TrainingConfig toConfig(StartRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("训练配置不能为空");
        }
        int sessionHands = request.sessionHands() == null ? 10 : request.sessionHands();
        if (sessionHands <= 0 || sessionHands > 100) {
            throw new IllegalArgumentException("训练手数必须在1到100之间");
        }
        int opponentCount = 1;

        SeatPosition heroPosition = parseSeatSetting(request.heroPosition(), true, null);
        SeatPosition gtoOpponentPosition = parseSeatSetting(request.gtoOpponentPosition(), true, null);
        Stage stage = parseStage(request.startStage());
        ScenarioType scenario = parseScenario(request.scenarioType());
        RelativePosition relativePosition = parseRelative(request.relativePosition());
        HandRange handRange = parseHandRange(request.handRange());
        HandType handType = parseHandType(request.handType());
        DrawType drawType = parseDrawType(request.drawType());
        List<SeatPosition> requestedOpponents = parseOpponentPositions(request.opponentPositions());
        BlindLevel blindLevel = parseBlindLevel(request.blindLevel());
        int totalStack = request.totalStack() == null ? 100 : request.totalStack();
        if (totalStack < 100 || totalStack > 200000) {
            throw new IllegalArgumentException("总筹码量必须在100到200000之间");
        }

        return new TrainingConfig(
                heroPosition,
                opponentCount,
                requestedOpponents,
                stage,
                scenario,
                gtoOpponentPosition,
                relativePosition,
                handRange,
                handType,
                drawType,
                sessionHands,
                blindLevel,
                totalStack
        );
    }

    private List<SpotState> generateHandSpots(TrainingConfig config, int handNumber, Random random) {
        SeatPosition heroPosition = resolveHeroPosition(config.heroPosition(), random);
        SeatPosition focusOpponent = resolveSingleGtoOpponent(heroPosition, config, random);
        List<SeatPosition> activeOpponents = List.of(focusOpponent);
        ScenarioType scenario = config.scenarioType() == ScenarioType.RANDOM
                ? randomEnum(ScenarioType.class, random, ScenarioType.RANDOM)
                : config.scenarioType();
        DrawType drawType = config.drawType() == DrawType.RANDOM || config.drawType() == DrawType.ANY
                ? drawForStage(Stage.FLOP, random)
                : config.drawType();
        HandType handType = config.handType() == HandType.RANDOM ? handTypeForScenario(scenario, Stage.PREFLOP, random) : config.handType();
        CardBundle cards = generateCards(Stage.RIVER, handType, drawType, config.handRange(), random);

        List<SpotState> handSpots = new ArrayList<>();
        for (Stage stage : List.of(Stage.PREFLOP, Stage.FLOP, Stage.TURN, Stage.RIVER)) {
            boolean gtoActsFirst = gtoActsBeforeHero(stage, heroPosition, focusOpponent);
            DecisionMode mode = decisionModeFor(stage, scenario, heroPosition, focusOpponent, random);
            List<String> board = boardForStage(cards.board(), stage);
            SpotEconomy economy = economyFor(stage, scenario, mode, config, random);
            ActionSet actionSet = buildActionSet(stage, scenario, mode, economy, config, random);
            double strength = evaluateStrength(cards.heroHand(), board, stage, handType, drawType);
            Map<String, ActionProfile> profiles = buildProfiles(actionSet, strength, scenario, stage, handType, drawType, mode, random);
            List<String> history = buildJourneyHistory(heroPosition, focusOpponent, stage, scenario, mode, economy, cards.board(), gtoActsFirst, config);

            handSpots.add(new SpotState(
                    "hand-" + handNumber + "-" + stage.name().toLowerCase(Locale.ROOT),
                    handNumber,
                    stage,
                    scenario,
                    mode,
                    heroPosition,
                    focusOpponent,
                    activeOpponents,
                    cards.heroHand(),
                    cards.opponentHand(),
                    board,
                    economy.potSize(),
                    economy.effectiveStack(),
                    economy.facingSize(),
                    history,
                    handDescriptor(handType, cards.heroHand(), stage),
                    drawDescriptor(drawType, stage),
                    profiles
            ));
        }
        return handSpots;
    }

    private SeatPosition resolveHeroPosition(SeatPosition heroPosition, Random random) {
        return heroPosition == null ? randomSeat(random, List.of()) : heroPosition;
    }

    private SeatPosition resolveSingleGtoOpponent(SeatPosition heroPosition, TrainingConfig config, Random random) {
        if (config.gtoOpponentPosition() != null && config.gtoOpponentPosition() != heroPosition) {
            return config.gtoOpponentPosition();
        }
        for (SeatPosition requested : config.requestedOpponents()) {
            if (requested != heroPosition) {
                return requested;
            }
        }
        return randomSeat(random, List.of(heroPosition));
    }

    private List<String> boardForStage(List<String> fullBoard, Stage stage) {
        return switch (stage) {
            case PREFLOP -> List.of();
            case FLOP -> fullBoard.subList(0, Math.min(3, fullBoard.size()));
            case TURN -> fullBoard.subList(0, Math.min(4, fullBoard.size()));
            case RIVER, RANDOM -> fullBoard.subList(0, Math.min(5, fullBoard.size()));
        };
    }

    private List<SeatPosition> resolveOpponents(SeatPosition heroPosition, TrainingConfig config, Random random) {
        Set<SeatPosition> candidates = new LinkedHashSet<>(SEAT_ORDER);
        candidates.remove(heroPosition);
        List<SeatPosition> ordered = new ArrayList<>(config.requestedOpponents());
        ordered.remove(heroPosition);
        for (SeatPosition seat : new ArrayList<>(ordered)) {
            candidates.remove(seat);
        }
        List<SeatPosition> result = new ArrayList<>(ordered.stream().limit(config.opponentCount()).toList());
        List<SeatPosition> remaining = new ArrayList<>(candidates);
        Collections.shuffle(remaining, random);
        for (SeatPosition seat : remaining) {
            if (result.size() >= config.opponentCount()) {
                break;
            }
            result.add(seat);
        }
        if (result.isEmpty()) {
            result.add(randomSeat(random, List.of(heroPosition)));
        }
        return result;
    }

    private SeatPosition resolveFocusOpponent(SeatPosition heroPosition, List<SeatPosition> opponents, TrainingConfig config, Random random) {
        List<SeatPosition> pool = new ArrayList<>(opponents);
        if (config.gtoOpponentPosition() != null && pool.contains(config.gtoOpponentPosition())) {
            pool = List.of(config.gtoOpponentPosition());
        }
        if (config.relativePosition() == RelativePosition.IP) {
            List<SeatPosition> ipPool = pool.stream().filter(seat -> heroHasPosition(heroPosition, seat)).toList();
            if (!ipPool.isEmpty()) {
                pool = ipPool;
            }
        }
        if (config.relativePosition() == RelativePosition.OOP) {
            List<SeatPosition> oopPool = pool.stream().filter(seat -> !heroHasPosition(heroPosition, seat)).toList();
            if (!oopPool.isEmpty()) {
                pool = oopPool;
            }
        }
        return pool.get(random.nextInt(pool.size()));
    }

    private boolean heroHasPosition(SeatPosition hero, SeatPosition villain) {
        List<SeatPosition> postflopOrder = List.of(SeatPosition.SB, SeatPosition.BB, SeatPosition.UTG, SeatPosition.HJ, SeatPosition.CO, SeatPosition.BTN);
        return postflopOrder.indexOf(hero) > postflopOrder.indexOf(villain);
    }

    private boolean gtoActsBeforeHero(Stage stage, SeatPosition hero, SeatPosition gtoOpponent) {
        if (stage == Stage.PREFLOP) {
            return SEAT_ORDER.indexOf(gtoOpponent) < SEAT_ORDER.indexOf(hero);
        }
        return heroHasPosition(hero, gtoOpponent);
    }

    private DrawType drawForStage(Stage stage, Random random) {
        if (stage == Stage.PREFLOP) {
            return DrawType.NONE;
        }
        List<DrawType> pool = stage == Stage.RIVER
                ? List.of(DrawType.NONE, DrawType.BACKDOOR)
                : List.of(DrawType.NONE, DrawType.FLUSH_DRAW, DrawType.STRAIGHT_DRAW, DrawType.COMBO_DRAW, DrawType.BACKDOOR);
        return pool.get(random.nextInt(pool.size()));
    }

    private HandType handTypeForScenario(ScenarioType scenario, Stage stage, Random random) {
        List<HandType> pool = new ArrayList<>(List.of(
                HandType.POCKET_PAIR,
                HandType.SUITED_BROADWAY,
                HandType.OFFSUIT_BROADWAY,
                HandType.SUITED_CONNECTOR,
                HandType.AX,
                HandType.BLUFF_CATCHER
        ));
        if (stage != Stage.PREFLOP) {
            pool.add(HandType.MONSTER);
        }
        if (scenario == ScenarioType.FACING_4BET || scenario == ScenarioType.FACING_5BET) {
            pool.add(HandType.PREMIUM);
        }
        return pool.get(random.nextInt(pool.size()));
    }

    private DecisionMode decisionModeFor(Stage stage, ScenarioType scenario, SeatPosition heroPosition,
                                        SeatPosition focusOpponent, Random random) {
        boolean gtoActsFirst = gtoActsBeforeHero(stage, heroPosition, focusOpponent);
        DecisionMode scenarioMode = scenarioDecisionMode(stage, scenario, random);
        if (stage != Stage.PREFLOP && !gtoActsFirst) {
            return DecisionMode.OPEN;
        }
        if (gtoActsFirst && scenario == ScenarioType.FACING_LIMP) {
            return DecisionMode.OPEN;
        }
        if (gtoActsFirst && scenarioMode == DecisionMode.OPEN) {
            return DecisionMode.OPEN;
        }
        if (gtoActsFirst) {
            return DecisionMode.FACING_BET;
        }
        return scenarioMode;
    }

    private DecisionMode scenarioDecisionMode(Stage stage, ScenarioType scenario, Random random) {
        if (stage == Stage.PREFLOP) {
            return switch (scenario) {
                case OPEN_RAISE, FACING_LIMP, RAISE_CALL -> DecisionMode.OPEN;
                case RANDOM -> random.nextBoolean() ? DecisionMode.OPEN : DecisionMode.FACING_BET;
                default -> DecisionMode.FACING_BET;
            };
        }
        return switch (scenario) {
            case OPEN_RAISE, RAISE_CALL -> random.nextBoolean() ? DecisionMode.OPEN : DecisionMode.FACING_BET;
            case FACING_RAISE, FACING_3BET, FACING_4BET, FACING_5BET, VS_SQUEEZE, FACING_ISOLATION -> DecisionMode.FACING_BET;
            case FACING_LIMP -> DecisionMode.OPEN;
            case RANDOM -> random.nextBoolean() ? DecisionMode.OPEN : DecisionMode.FACING_BET;
        };
    }

    private CardBundle generateCards(Stage stage, HandType handType, DrawType drawType, HandRange handRange, Random random) {
        List<String> heroPool = switch (handType) {
            case PREMIUM -> PRE_FLOP_PREMIUMS;
            case POCKET_PAIR -> POCKET_PAIRS;
            case SUITED_BROADWAY -> SUITED_BROADWAYS;
            case OFFSUIT_BROADWAY -> OFFSUIT_BROADWAYS;
            case SUITED_CONNECTOR -> SUITED_CONNECTORS;
            case AX -> AX_COMBOS;
            case BLUFF_CATCHER -> BLUFF_CATCHERS;
            case MONSTER -> MONSTER_POST;
            case RANDOM -> mergePools(PRE_FLOP_PREMIUMS, POCKET_PAIRS, SUITED_BROADWAYS, OFFSUIT_BROADWAYS, SUITED_CONNECTORS, AX_COMBOS, BLUFF_CATCHERS);
        };
        heroPool = applyRangeBias(heroPool, handRange, random);

        String heroText = heroPool.get(random.nextInt(heroPool.size()));
        Combo heroHand = Combo.parse(heroText);
        List<String> board = new ArrayList<>();

        if (stage == Stage.PREFLOP) {
            return new CardBundle(heroHand, generateOpponentHand(heroHand, board, random), board);
        }

        List<String> boardPool = switch (drawType) {
            case FLUSH_DRAW -> stage == Stage.FLOP ? FLUSH_DRAW_FLOPS : TURN_BOARDS;
            case STRAIGHT_DRAW -> stage == Stage.FLOP ? STRAIGHT_DRAW_FLOPS : TURN_BOARDS;
            case COMBO_DRAW -> stage == Stage.FLOP ? COMBO_DRAW_FLOPS : TURN_BOARDS;
            case BACKDOOR, NONE, ANY, RANDOM -> stage == Stage.FLOP ? FLUSH_DRAW_FLOPS : stage == Stage.TURN ? TURN_BOARDS : RIVER_DYNAMIC_BOARDS;
        };

        int attempts = 0;
        while (attempts++ < 50) {
            String boardText = boardPool.get(random.nextInt(boardPool.size()));
            board = splitBoard(boardText);
            if (stage == Stage.TURN && board.size() > 4) {
                board = board.subList(0, 4);
            }
            if (stage == Stage.FLOP && board.size() > 3) {
                board = board.subList(0, 3);
            }
            if (stage == Stage.RIVER && board.size() < 5) {
                board = splitBoard(RIVER_DYNAMIC_BOARDS.get(random.nextInt(RIVER_DYNAMIC_BOARDS.size())));
            }
            if (!conflicts(heroHand, board)) {
                return new CardBundle(heroHand, generateOpponentHand(heroHand, board, random), board);
            }
        }

        List<String> fallbackBoards = stage == Stage.RIVER ? splitBoard(DRY_RIVER_BOARDS.get(random.nextInt(DRY_RIVER_BOARDS.size())))
                : stage == Stage.TURN ? splitBoard(TURN_BOARDS.get(random.nextInt(TURN_BOARDS.size())))
                : splitBoard(FLUSH_DRAW_FLOPS.get(random.nextInt(FLUSH_DRAW_FLOPS.size())));
        return new CardBundle(heroHand, generateOpponentHand(heroHand, fallbackBoards, random), fallbackBoards);
    }

    private Combo generateOpponentHand(Combo heroHand, List<String> board, Random random) {
        List<Card> deck = new ArrayList<>();
        for (int rank = 2; rank <= 14; rank++) {
            for (char suit : new char[]{'s', 'h', 'd', 'c'}) {
                Card card = new Card(rank, suit);
                if (!card.equals(heroHand.first()) && !card.equals(heroHand.second())
                        && board.stream().map(Card::parse).noneMatch(card::equals)) {
                    deck.add(card);
                }
            }
        }
        Collections.shuffle(deck, random);
        return new Combo(deck.get(0), deck.get(1));
    }

    private SpotEconomy economyFor(Stage stage, ScenarioType scenario, DecisionMode mode, TrainingConfig config, Random random) {
        double totalStack = config.totalStack();
        double bigBlind = config.blindLevel().bigBlind();
        double stack = switch (stage) {
            case PREFLOP -> totalStack;
            case FLOP -> totalStack * 0.88;
            case TURN -> totalStack * 0.74;
            case RIVER -> totalStack * 0.58;
            case RANDOM -> totalStack * 0.80;
        };
        double basePotBb = switch (stage) {
            case PREFLOP -> mode == DecisionMode.OPEN ? 1.5 : 4.5;
            case FLOP -> scenario == ScenarioType.FACING_3BET ? 9.0 : 3.75;
            case TURN -> scenario == ScenarioType.FACING_3BET ? 15.5 : 8.0;
            case RIVER -> scenario == ScenarioType.FACING_4BET ? 36.0 : 14.0;
            case RANDOM -> 10.0;
        };
        double pot = basePotBb * bigBlind;
        double facingSize = switch (stage) {
            case PREFLOP -> switch (scenario) {
                case FACING_RAISE -> 2.5 * bigBlind;
                case FACING_3BET -> 9.0 * bigBlind;
                case FACING_4BET -> 22.0 * bigBlind;
                case FACING_5BET -> 44.0 * bigBlind;
                case VS_SQUEEZE -> 12.0 * bigBlind;
                case FACING_ISOLATION -> 5.5 * bigBlind;
                default -> 0.0;
            };
            case FLOP -> random.nextBoolean() ? pot * 0.33 : pot * 0.66;
            case TURN -> random.nextBoolean() ? pot * 0.50 : pot * 0.75;
            case RIVER -> random.nextBoolean() ? pot * 0.80 : stack;
            case RANDOM -> pot * 0.5;
        };
        return new SpotEconomy(pot, stack, Math.min(facingSize, stack));
    }

    private ActionSet buildActionSet(Stage stage, ScenarioType scenario, DecisionMode mode, SpotEconomy economy,
                                     TrainingConfig config, Random random) {
        List<ActionOptionView> options = new ArrayList<>();
        double bigBlind = config.blindLevel().bigBlind();
        if (stage == Stage.PREFLOP && mode == DecisionMode.OPEN) {
            if (scenario == ScenarioType.FACING_LIMP) {
                options.add(new ActionOptionView("CHECK", "Check Behind", "neutral"));
                options.add(new ActionOptionView("ISO_4", actionLabel("Bet", 4.0 * bigBlind, economy.effectiveStack()), "aggressive"));
                options.add(new ActionOptionView("ISO_5_5", actionLabel("Bet", 5.5 * bigBlind, economy.effectiveStack()), "aggressive"));
                options.add(new ActionOptionView("ISO_7", actionLabel("Bet", 7.0 * bigBlind, economy.effectiveStack()), "pressure"));
            } else {
                options.add(new ActionOptionView("FOLD", "Fold", "danger"));
                options.add(new ActionOptionView("OPEN_2_2", actionLabel("Raise", 2.2 * bigBlind, economy.effectiveStack()), "aggressive"));
                options.add(new ActionOptionView("OPEN_2_5", actionLabel("Raise", 2.5 * bigBlind, economy.effectiveStack()), "aggressive"));
                options.add(new ActionOptionView("OPEN_3", actionLabel("Raise", 3.0 * bigBlind, economy.effectiveStack()), "pressure"));
            }
            return new ActionSet(options, mode);
        }

        if (stage == Stage.PREFLOP) {
            options.add(new ActionOptionView("FOLD", "Fold", "danger"));
            options.add(new ActionOptionView("CALL", "Call", "neutral"));
            if (scenario == ScenarioType.FACING_5BET) {
                return new ActionSet(options, mode);
            }
            String aggressiveLabel = switch (scenario) {
                case FACING_4BET -> actionLabel("Raise", 48.0 * bigBlind, economy.effectiveStack());
                case FACING_3BET -> actionLabel("Raise", 22.0 * bigBlind, economy.effectiveStack());
                case VS_SQUEEZE -> actionLabel("Raise", 24.0 * bigBlind, economy.effectiveStack());
                case FACING_ISOLATION -> actionLabel("Raise", 14.0 * bigBlind, economy.effectiveStack());
                default -> actionLabel("Raise", 9.5 * bigBlind, economy.effectiveStack());
            };
            options.add(new ActionOptionView("AGG_SMALL", aggressiveLabel, "aggressive"));
            options.add(new ActionOptionView("ALLIN", "All-in " + amountLabel(economy.effectiveStack()), "pressure"));
            return new ActionSet(options, mode);
        }

        if (mode == DecisionMode.OPEN) {
            options.add(new ActionOptionView("CHECK", "Check", "neutral"));
            options.add(new ActionOptionView("BET_33", actionLabel("Bet", economy.potSize() * (stage == Stage.RIVER ? 0.50 : 0.33), economy.effectiveStack()), "aggressive"));
            options.add(new ActionOptionView("BET_75", actionLabel("Bet", economy.potSize() * (stage == Stage.RIVER ? 1.00 : 0.75), economy.effectiveStack()), "pressure"));
            if (stage == Stage.RIVER || random.nextBoolean()) {
                options.add(new ActionOptionView("ALLIN", "All-in " + amountLabel(economy.effectiveStack()), "danger"));
            }
        } else {
            options.add(new ActionOptionView("FOLD", "Fold", "danger"));
            options.add(new ActionOptionView("CALL", "Call", "neutral"));
            options.add(new ActionOptionView("RAISE_SMALL", actionLabel("Raise", economy.facingSize() * (stage == Stage.RIVER ? 2.5 : 3.0), economy.effectiveStack()), "aggressive"));
            if (stage == Stage.TURN || stage == Stage.RIVER || random.nextBoolean()) {
                options.add(new ActionOptionView("ALLIN", "All-in " + amountLabel(economy.effectiveStack()), "pressure"));
            }
        }
        return new ActionSet(options, mode);
    }

    private Map<String, ActionProfile> buildProfiles(ActionSet actionSet, double strength, ScenarioType scenario,
                                                     Stage stage, HandType handType, DrawType drawType,
                                                     DecisionMode mode, Random random) {
        Map<String, Double> raw = new LinkedHashMap<>();
        for (ActionOptionView option : actionSet.options()) {
            raw.put(option.code(), rawWeight(option.code(), strength, scenario, stage, handType, drawType, mode));
        }

        double total = raw.values().stream().mapToDouble(Double::doubleValue).sum();
        Map<String, ActionProfile> profiles = new LinkedHashMap<>();
        double bestEv = Double.NEGATIVE_INFINITY;
        for (ActionOptionView option : actionSet.options()) {
            double freq = raw.get(option.code()) / total;
            double ev = actionEv(option.code(), strength, stage, mode, freq, random);
            bestEv = Math.max(bestEv, ev);
            profiles.put(option.code(), new ActionProfile(option, freq, ev));
        }

        final double finalBestEv = bestEv;
        profiles.replaceAll((code, profile) -> profile.withGap(finalBestEv - profile.ev()));
        return profiles;
    }

    private double rawWeight(String code, double strength, ScenarioType scenario, Stage stage,
                             HandType handType, DrawType drawType, DecisionMode mode) {
        double made = strength;
        double draws = switch (drawType) {
            case FLUSH_DRAW -> 0.55;
            case STRAIGHT_DRAW -> 0.48;
            case COMBO_DRAW -> 0.75;
            case BACKDOOR -> 0.22;
            default -> 0.0;
        };
        double polar = handType == HandType.MONSTER ? 0.25 : handType == HandType.BLUFF_CATCHER ? -0.1 : 0.0;
        return switch (code) {
            case "FOLD" -> clamp(0.02 + Math.pow(1.0 - made, 2.3) + (mode == DecisionMode.FACING_BET ? 0.12 : 0.0), 0.01, 3.0);
            case "CALL" -> clamp(0.12 + 0.85 * (1.0 - Math.abs(made - 0.58)) + 0.32 * draws, 0.05, 2.4);
            case "CHECK" -> clamp(0.18 + 0.72 * (1.0 - made) + 0.12 * draws - polar, 0.05, 2.4);
            case "OPEN_2_2" -> clamp(0.30 + 0.95 * made + 0.05 * draws, 0.05, 2.8);
            case "OPEN_2_5" -> clamp(0.35 + 1.10 * made + 0.04 * draws, 0.05, 3.0);
            case "OPEN_3", "ISO_4", "ISO_5_5", "ISO_7" -> clamp(0.20 + 0.82 * made + 0.10 * polar, 0.03, 2.2);
            case "BET_33" -> clamp(0.12 + 0.48 * made + 0.46 * draws + (scenario == ScenarioType.OPEN_RAISE ? 0.15 : 0.0), 0.03, 2.5);
            case "BET_75" -> clamp(0.06 + 0.88 * made + 0.20 * draws + polar, 0.02, 2.6);
            case "RAISE_SMALL", "AGG_SMALL" -> clamp(0.05 + 0.75 * made + 0.22 * draws + polar, 0.02, 2.5);
            case "ALLIN" -> clamp(0.01 + 0.95 * made + 0.14 * draws + Math.max(0.0, polar), 0.01, 2.4);
            default -> 0.1;
        };
    }

    private double actionEv(String code, double strength, Stage stage, DecisionMode mode, double freq, Random random) {
        double stageScale = switch (stage) {
            case PREFLOP -> 4.5;
            case FLOP -> 7.5;
            case TURN -> 11.0;
            case RIVER -> 15.0;
            case RANDOM -> 8.0;
        };
        double base = (strength - 0.48) * stageScale;
        double strategyFit = (freq - 0.18) * stageScale * 2.1;
        double style = switch (code) {
            case "FOLD" -> -Math.max(0.0, strength - 0.25) * stageScale;
            case "CALL", "CHECK" -> base - 0.35 + strategyFit;
            case "BET_33", "OPEN_2_2", "OPEN_2_5", "ISO_4" -> base + 0.25 + strategyFit;
            case "BET_75", "OPEN_3", "ISO_5_5", "ISO_7", "RAISE_SMALL", "AGG_SMALL" -> base + 0.45 + strategyFit;
            case "ALLIN" -> base + strategyFit + (strength > 0.72 ? 1.2 : -2.2);
            default -> base;
        };
        return round(style + (random.nextDouble() - 0.5) * 0.18);
    }

    private double evaluateStrength(Combo heroHand, List<String> board, Stage stage, HandType handType, DrawType drawType) {
        if (stage == Stage.PREFLOP) {
            return preflopStrength(heroHand);
        }
        Card[] cards = merge(heroHand, board);
        long score = board.size() >= 3 ? com.solvergto.poker.HandEvaluator.bestScore(cards) : 0L;
        int category = com.solvergto.poker.HandEvaluator.category(score);
        double made = switch (category) {
            case 8 -> 1.0;
            case 7 -> 0.97;
            case 6 -> 0.92;
            case 5 -> 0.86;
            case 4 -> 0.80;
            case 3 -> 0.72;
            case 2 -> 0.63;
            case 1 -> 0.48;
            default -> 0.18;
        };
        double modifier = switch (drawType) {
            case COMBO_DRAW -> 0.12;
            case FLUSH_DRAW, STRAIGHT_DRAW -> 0.06;
            case BACKDOOR -> 0.02;
            default -> 0.0;
        };
        if (handType == HandType.MONSTER) {
            modifier += 0.12;
        }
        if (handType == HandType.BLUFF_CATCHER) {
            modifier -= 0.04;
        }
        return clamp(made + modifier, 0.02, 0.99);
    }

    private double preflopStrength(Combo combo) {
        int high = Math.max(combo.first().rank(), combo.second().rank());
        int low = Math.min(combo.first().rank(), combo.second().rank());
        if (high == low) {
            return clamp(0.50 + 0.48 * ((high - 2) / 12.0), 0.2, 0.99);
        }
        double value = 0.14 + 0.30 * ((high - 2) / 12.0) + 0.16 * ((low - 2) / 12.0);
        if (combo.first().suit() == combo.second().suit()) {
            value += 0.12;
        }
        int gap = high - low;
        if (gap == 1) {
            value += 0.08;
        } else if (gap == 2) {
            value += 0.04;
        }
        if (high == 14) {
            value += 0.06;
        }
        return clamp(value, 0.08, 0.96);
    }

    private List<String> applyRangeBias(List<String> heroPool, HandRange handRange, Random random) {
        List<String> biased = switch (handRange) {
            case RANDOM, LINEAR -> heroPool;
            case PREMIUM_HEAVY -> !PRE_FLOP_PREMIUMS.isEmpty() ? mergePools(PRE_FLOP_PREMIUMS, heroPool) : heroPool;
            case CAPPED -> heroPool.stream().filter(combo -> !PRE_FLOP_PREMIUMS.contains(combo) && !MONSTER_POST.contains(combo)).toList();
            case BLUFF_HEAVY -> mergePools(SUITED_CONNECTORS, AX_COMBOS, BLUFF_CATCHERS, heroPool);
        };
        return biased.isEmpty() ? heroPool : biased;
    }

    private List<String> buildHistoryLines(SeatPosition heroPosition, SeatPosition focusOpponent, Stage stage,
                                           ScenarioType scenario, DecisionMode mode, SpotEconomy economy,
                                           List<String> board, Random random) {
        List<String> lines = new ArrayList<>();
        if (stage == Stage.PREFLOP) {
            lines.addAll(preflopHistory(heroPosition, focusOpponent, scenario, mode, economy, false));
            return lines;
        }

        lines.addAll(preflopContext(heroPosition, focusOpponent, scenario));
        lines.add("Flop " + String.join(" ", board.subList(0, 3)));
        if (stage == Stage.FLOP) {
            lines.addAll(streetAction(stage, heroPosition, focusOpponent, mode, economy, false));
            return lines;
        }
        lines.addAll(streetAction(Stage.FLOP, heroPosition, focusOpponent, random.nextBoolean() ? DecisionMode.OPEN : DecisionMode.FACING_BET, economy, false));
        lines.add("Turn " + board.get(3));
        if (stage == Stage.TURN) {
            lines.addAll(streetAction(stage, heroPosition, focusOpponent, mode, economy, false));
            return lines;
        }
        lines.addAll(streetAction(Stage.TURN, heroPosition, focusOpponent, random.nextBoolean() ? DecisionMode.OPEN : DecisionMode.FACING_BET, economy, false));
        lines.add("River " + board.get(4));
        lines.addAll(streetAction(stage, heroPosition, focusOpponent, mode, economy, false));
        return lines;
    }

    private List<String> preflopHistory(SeatPosition heroPosition, SeatPosition focusOpponent,
                                        ScenarioType scenario, DecisionMode mode, SpotEconomy economy,
                                        boolean gtoActsFirst) {
        return preflopHistory(heroPosition, focusOpponent, scenario, mode, economy, gtoActsFirst, BlindLevel.DEFAULT);
    }

    private List<String> preflopHistory(SeatPosition heroPosition, SeatPosition focusOpponent,
                                        ScenarioType scenario, DecisionMode mode, SpotEconomy economy,
                                        boolean gtoActsFirst, BlindLevel blindLevel) {
        double bb = blindLevel.bigBlind();
        return switch (scenario) {
            case OPEN_RAISE -> mode == DecisionMode.FACING_BET || gtoActsFirst
                    ? List.of(focusOpponent + " opens to " + amountLabel(2.5 * bb), heroPosition + " to act")
                    : List.of(heroPosition + " unopened to act");
            case FACING_RAISE -> List.of(focusOpponent + " opens to " + amountLabel(2.5 * bb), heroPosition + " to act");
            case FACING_3BET -> List.of(heroPosition + " opens " + amountLabel(2.5 * bb), focusOpponent + " 3-bets to " + amountLabel(9 * bb), heroPosition + " to act");
            case FACING_4BET -> List.of(focusOpponent + " opens " + amountLabel(2.5 * bb), heroPosition + " 3-bets to " + amountLabel(9 * bb), focusOpponent + " 4-bets to " + amountLabel(22 * bb), heroPosition + " to act");
            case FACING_5BET -> List.of(heroPosition + " opens " + amountLabel(2.5 * bb), focusOpponent + " 3-bets to " + amountLabel(9 * bb), heroPosition + " 4-bets to " + amountLabel(22 * bb), focusOpponent + " jams", heroPosition + " to act");
            case RAISE_CALL -> gtoActsFirst
                    ? List.of(focusOpponent + " raises " + amountLabel(2.5 * bb), heroPosition + " calls", focusOpponent + " checks option", heroPosition + " to continue plan")
                    : List.of(heroPosition + " iso opens " + amountLabel(3 * bb), focusOpponent + " calls", heroPosition + " to continue plan");
            case VS_SQUEEZE -> List.of(focusOpponent + " applies squeeze pressure to " + amountLabel(12 * bb), heroPosition + " to act");
            case FACING_LIMP -> List.of(focusOpponent + " limps", heroPosition + " to act");
            case FACING_ISOLATION -> List.of(focusOpponent + " isolates to " + amountLabel(5.5 * bb), heroPosition + " to act");
            case RANDOM -> mode == DecisionMode.OPEN
                    ? gtoActsFirst
                            ? List.of(focusOpponent + " checks option", heroPosition + " to act")
                            : List.of(heroPosition + " unopened to act")
                    : List.of(focusOpponent + " applies pressure", heroPosition + " to act");
        };
    }

    private List<String> preflopContext(SeatPosition heroPosition, SeatPosition focusOpponent, ScenarioType scenario) {
        return switch (scenario) {
            case FACING_3BET -> List.of(heroPosition + " calls a 3-bet OOP");
            case FACING_4BET -> List.of(heroPosition + " calls a 4-bet");
            case FACING_5BET -> List.of(heroPosition + " hero-call range reaches river");
            case RAISE_CALL -> List.of(heroPosition + " raises preflop", focusOpponent + " calls");
            case VS_SQUEEZE -> List.of(heroPosition + " defends versus squeeze pressure", focusOpponent + " continues");
            case FACING_LIMP -> List.of(focusOpponent + " limps", heroPosition + " checks behind");
            case FACING_ISOLATION -> List.of(heroPosition + " defends after an iso raise");
            default -> List.of(heroPosition + " and " + focusOpponent + " reach a heads-up pot");
        };
    }

    private List<String> buildJourneyHistory(SeatPosition heroPosition, SeatPosition focusOpponent, Stage stage,
                                             ScenarioType scenario, DecisionMode mode, SpotEconomy economy,
                                             List<String> fullBoard, boolean gtoActsFirst, TrainingConfig config) {
        List<String> lines = new ArrayList<>();
        lines.addAll(preflopHistory(heroPosition, focusOpponent, scenario, mode, economy, gtoActsFirst && stage == Stage.PREFLOP, config.blindLevel()));
        if (stage == Stage.PREFLOP) {
            return lines;
        }

        lines.add("GTO opponent action is auto-resolved");
        lines.add("Flop " + String.join(" ", fullBoard.subList(0, 3)));
        if (stage == Stage.FLOP) {
            lines.addAll(streetAction(stage, heroPosition, focusOpponent, mode, economy, gtoActsFirst));
            return lines;
        }

        lines.add(heroPosition + " and " + focusOpponent + " continue to turn");
        lines.add("Turn " + fullBoard.get(3));
        if (stage == Stage.TURN) {
            lines.addAll(streetAction(stage, heroPosition, focusOpponent, mode, economy, gtoActsFirst));
            return lines;
        }

        lines.add(heroPosition + " and " + focusOpponent + " continue to river");
        lines.add("River " + fullBoard.get(4));
        lines.addAll(streetAction(stage, heroPosition, focusOpponent, mode, economy, gtoActsFirst));
        return lines;
    }

    private List<String> streetAction(Stage street, SeatPosition heroPosition, SeatPosition focusOpponent,
                                      DecisionMode mode, SpotEconomy economy, boolean gtoActsFirst) {
        if (mode == DecisionMode.OPEN) {
            if (gtoActsFirst) {
                return List.of(focusOpponent + " checks", heroPosition + " to act in " + street.name().toLowerCase(Locale.ROOT));
            }
            return List.of(heroPosition + " to act in " + street.name().toLowerCase(Locale.ROOT));
        }
        double size = street == Stage.RIVER ? economy.facingSize() : Math.max(1.0, economy.potSize() * (street == Stage.FLOP ? 0.33 : 0.66));
        return List.of(focusOpponent + " bets " + round(size), heroPosition + " to act");
    }

    private ScoreBand classify(ActionProfile profile, ActionProfile bestProfile) {
        if (Objects.equals(profile.option().code(), bestProfile.option().code())) {
            return ScoreBand.BEST;
        }
        if (profile.frequency() >= 0.28 && profile.evGap() <= 1.1) {
            return ScoreBand.CORRECT;
        }
        if (profile.frequency() >= 0.12 || profile.evGap() <= 2.1) {
            return ScoreBand.QUESTIONABLE;
        }
        if (profile.frequency() >= 0.04 || profile.evGap() <= 4.0) {
            return ScoreBand.ERROR;
        }
        return ScoreBand.BLUNDER;
    }

    private boolean shouldCompleteHand(SpotState spot, ActionProfile profile) {
        return spot.stage() == Stage.RIVER || isAllInAction(profile) || isFoldAction(profile);
    }

    private boolean isAllInAction(ActionProfile profile) {
        return "ALLIN".equalsIgnoreCase(profile.option().code());
    }

    private boolean isFoldAction(ActionProfile profile) {
        return "FOLD".equalsIgnoreCase(profile.option().code());
    }

    private FeedbackView buildFeedback(SpotState spot, ActionProfile chosen, ScoreBand band) {
        Map<String, Double> frequencies = new LinkedHashMap<>();
        Map<String, Double> evs = new LinkedHashMap<>();
        for (ActionProfile profile : spot.profileByCode().values()) {
            frequencies.put(profile.option().label(), round(profile.frequency()));
            evs.put(profile.option().label(), round(profile.ev()));
        }

        String bestAction = spot.bestProfile().option().label();
        String summary = band.label() + "，本次行动获得 " + band.points() + " 分。";
        String explanation = switch (band) {
            case BEST -> "你选择了当前策略里最核心的动作，频率和EV都贴近最优。";
            case CORRECT -> "这不是最高频动作，但仍属于GTO允许的正当混合，长期不会损失太多。";
            case QUESTIONABLE -> "这是一个极低频或边缘动作，偶尔出现合理，但不建议作为默认习惯。";
            case ERROR -> "这个动作在当前范围和节点里明显偏离主策略，长期会造成可见EV流失。";
            case BLUNDER -> "这是一个极低EV甚至灾难性的选择，建议优先修正这类模式。";
        };

        return new FeedbackView(
                chosen.option().label(),
                band.label(),
                band.points(),
                bestAction,
                summary,
                explanation + " 推荐优先记住：" + bestAction + "。",
                frequencies,
                evs
        );
    }

    private SessionView toSessionView(SessionState state) {
        SpotState spot = state.completed() || state.awaitingNextHand() ? null : state.currentSpot();
        return new SessionView(
                state.sessionId(),
                state.completed(),
                state.awaitingNextHand(),
                currentHandNumber(state, spot),
                state.config().sessionHands(),
                "Cash · 6Max · NL Demo · Trainer",
                configSummary(state.config()),
                state.stats().toView(state.config().sessionHands()),
                spot == null ? null : toSpotView(spot),
                state.handHistoryViews()
        );
    }

    private int currentHandNumber(SessionState state, SpotState spot) {
        if (spot != null) {
            return spot.handNumber();
        }
        if (state.awaitingNextHand()) {
            return Math.max(1, state.stats().handsPlayed());
        }
        return state.config().sessionHands();
    }

    private ConfigSummaryView configSummary(TrainingConfig config) {
        String opponents = config.requestedOpponents().isEmpty()
                ? "1 个 GTO 对手（自动分配）"
                : "1 个 GTO 对手：" + joinSeats(config.requestedOpponents().stream().limit(1).toList());
        return new ConfigSummaryView(
                "Cash · 6Max · NL · 盲注 " + config.blindLevel().label() + " · 筹码 " + amountLabel(config.totalStack()),
                seatLabel(config.heroPosition()),
                opponents,
                "完整四街",
                labelForScenario(config.scenarioType()),
                labelForRelative(config.relativePosition()),
                labelForHandRange(config.handRange()) + " / " + labelForHandType(config.handType()) + " / " + labelForDraw(config.drawType())
        );
    }

    private SpotView toSpotView(SpotState spot) {
        List<SeatView> seats = new ArrayList<>();
        for (SeatPosition seat : SEAT_ORDER) {
            boolean hero = seat == spot.heroPosition();
            boolean activeOpponent = spot.activeOpponents().contains(seat);
            boolean gto = seat == spot.focusOpponentPosition();
            boolean dealer = seat == SeatPosition.BTN;
            double currentBet = gto && spot.decisionMode() == DecisionMode.FACING_BET ? spot.facingSize() : 0.0;
            seats.add(new SeatView(
                    seat.name(),
                    hero,
                    gto,
                    activeOpponent,
                    dealer,
                    !hero && !activeOpponent,
                    hero ? "HERO" : gto ? "GTO" : activeOpponent ? "VILLAIN" : "",
                    hero || activeOpponent ? round(spot.effectiveStack()) : 0.0,
                    round(currentBet)
            ));
        }

        return new SpotView(
                spot.spotId(),
                labelForStage(spot.stage()),
                labelForScenario(spot.scenario()),
                spot.decisionMode().name(),
                spot.heroPosition().name(),
                spot.focusOpponentPosition().name(),
                seats,
                spot.board(),
                spot.heroHand().text(),
                round(spot.potSize()),
                round(spot.effectiveStack()),
                spot.actionHistory(),
                spot.handDescriptor(),
                spot.drawDescriptor(),
                spot.profileByCode().values().stream().map(profile -> profile.option()).toList()
        );
    }

    private SeatPosition parseSeatSetting(String text, boolean allowRandom, SeatPosition defaultValue) {
        if (text == null || text.isBlank()) {
            return defaultValue;
        }
        String normalized = text.trim().toUpperCase(Locale.ROOT);
        if (allowRandom && "RANDOM".equals(normalized)) {
            return null;
        }
        return SeatPosition.parse(normalized);
    }

    private List<SeatPosition> parseOpponentPositions(List<String> values) {
        if (values == null) {
            return List.of();
        }
        List<SeatPosition> out = new ArrayList<>();
        for (String value : values) {
            if (value == null || value.isBlank()) {
                continue;
            }
            if ("RANDOM".equalsIgnoreCase(value)) {
                continue;
            }
            out.add(SeatPosition.parse(value));
        }
        return out;
    }

    private Stage parseStage(String text) {
        if (text == null || text.isBlank()) {
            return Stage.RANDOM;
        }
        return Stage.parse(text);
    }

    private ScenarioType parseScenario(String text) {
        if (text == null || text.isBlank()) {
            return ScenarioType.RANDOM;
        }
        return ScenarioType.parse(text);
    }

    private RelativePosition parseRelative(String text) {
        if (text == null || text.isBlank()) {
            return RelativePosition.RANDOM;
        }
        return RelativePosition.parse(text);
    }

    private HandRange parseHandRange(String text) {
        if (text == null || text.isBlank()) {
            return HandRange.RANDOM;
        }
        return HandRange.parse(text);
    }

    private HandType parseHandType(String text) {
        if (text == null || text.isBlank()) {
            return HandType.RANDOM;
        }
        return HandType.parse(text);
    }

    private DrawType parseDrawType(String text) {
        if (text == null || text.isBlank()) {
            return DrawType.RANDOM;
        }
        return DrawType.parse(text);
    }

    private BlindLevel parseBlindLevel(String text) {
        if (text == null || text.isBlank()) {
            return BlindLevel.DEFAULT;
        }
        String normalized = text.trim().replace(" ", "");
        if ("2".equals(normalized)) {
            normalized = "2/4";
        } else if ("4".equals(normalized)) {
            normalized = "4/8";
        }
        String[] parts = normalized.split("/");
        if (parts.length != 2) {
            throw new IllegalArgumentException("盲注级别格式错误，请使用1/2、2/4、5/10等格式");
        }
        try {
            double smallBlind = Double.parseDouble(parts[0]);
            double bigBlind = Double.parseDouble(parts[1]);
            if (smallBlind <= 0 || bigBlind <= 0 || smallBlind >= bigBlind) {
                throw new IllegalArgumentException("盲注级别必须满足小盲大于0且小盲小于大盲");
            }
            return new BlindLevel(normalized, smallBlind, bigBlind);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("盲注级别只能包含数字和斜杠", exception);
        }
    }

    private SeatPosition randomSeat(Random random, List<SeatPosition> excluded) {
        List<SeatPosition> available = new ArrayList<>(SEAT_ORDER);
        available.removeAll(excluded);
        return available.get(random.nextInt(available.size()));
    }

    private <E extends Enum<E>> E randomEnum(Class<E> type, Random random, E excluded) {
        List<E> values = new ArrayList<>(Arrays.asList(type.getEnumConstants()));
        values.remove(excluded);
        return values.get(random.nextInt(values.size()));
    }

    private boolean conflicts(Combo heroHand, List<String> board) {
        Card[] boardCards = board.stream().map(Card::parse).toArray(Card[]::new);
        return heroHand.conflictsWith(boardCards);
    }

    private List<String> splitBoard(String text) {
        List<String> out = new ArrayList<>();
        for (int i = 0; i < text.length(); i += 2) {
            out.add(text.substring(i, i + 2));
        }
        return out;
    }

    private Card[] merge(Combo combo, List<String> board) {
        Card[] cards = new Card[board.size() + 2];
        cards[0] = combo.first();
        cards[1] = combo.second();
        for (int i = 0; i < board.size(); i++) {
            cards[i + 2] = Card.parse(board.get(i));
        }
        return cards;
    }

    @SafeVarargs
    private final List<String> mergePools(List<String>... pools) {
        List<String> out = new ArrayList<>();
        for (List<String> pool : pools) {
            out.addAll(pool);
        }
        return out;
    }

    private String handDescriptor(HandType handType, Combo combo, Stage stage) {
        return labelForHandType(handType) + (stage == Stage.PREFLOP ? " · 起手牌训练" : " · 决策样本");
    }

    private String drawDescriptor(DrawType drawType, Stage stage) {
        return stage == Stage.PREFLOP ? "无公共牌" : labelForDraw(drawType);
    }

    private String joinSeats(List<SeatPosition> seats) {
        return seats.stream().map(Enum::name).reduce((left, right) -> left + ", " + right).orElse("");
    }

    private String seatLabel(SeatPosition seat) {
        return seat == null ? "随机" : seat.name();
    }

    private String labelForStage(Stage stage) {
        return switch (stage) {
            case PREFLOP -> "翻前";
            case FLOP -> "翻牌";
            case TURN -> "转牌";
            case RIVER -> "河牌";
            case RANDOM -> "随机";
        };
    }

    private static String labelStageForHistory(Stage stage) {
        return switch (stage) {
            case PREFLOP -> "翻前";
            case FLOP -> "翻牌";
            case TURN -> "转牌";
            case RIVER -> "河牌";
            case RANDOM -> "随机";
        };
    }

    private String labelForScenario(ScenarioType scenario) {
        return switch (scenario) {
            case RANDOM -> "随机";
            case OPEN_RAISE -> "加注开池";
            case FACING_RAISE -> "面对加注";
            case FACING_3BET -> "面对3bet";
            case FACING_4BET -> "面对4bet";
            case FACING_5BET -> "面对5bet";
            case RAISE_CALL -> "面对加注-跟注";
            case VS_SQUEEZE -> "对抗挤压";
            case FACING_LIMP -> "面对溜入";
            case FACING_ISOLATION -> "面对隔离";
        };
    }

    private static String labelScenarioForHistory(ScenarioType scenario) {
        return switch (scenario) {
            case RANDOM -> "随机";
            case OPEN_RAISE -> "加注开池";
            case FACING_RAISE -> "面对加注";
            case FACING_3BET -> "面对3bet";
            case FACING_4BET -> "面对4bet";
            case FACING_5BET -> "面对5bet";
            case RAISE_CALL -> "面对加注-跟注";
            case VS_SQUEEZE -> "对抗挤压";
            case FACING_LIMP -> "面对溜入";
            case FACING_ISOLATION -> "面对隔离";
        };
    }

    private String labelForRelative(RelativePosition relativePosition) {
        return switch (relativePosition) {
            case RANDOM -> "随机";
            case IP -> "IP（相对有利）";
            case OOP -> "OOP（相对不利）";
        };
    }

    private String labelForHandRange(HandRange handRange) {
        return switch (handRange) {
            case RANDOM -> "随机范围";
            case PREMIUM_HEAVY -> "强牌偏多";
            case LINEAR -> "线性范围";
            case CAPPED -> "封顶较低";
            case BLUFF_HEAVY -> "诈唬偏多";
        };
    }

    private String labelForHandType(HandType handType) {
        return switch (handType) {
            case RANDOM -> "随机手牌";
            case PREMIUM -> "Premium";
            case POCKET_PAIR -> "口袋对子";
            case SUITED_BROADWAY -> "同花Broadway";
            case OFFSUIT_BROADWAY -> "非同花Broadway";
            case SUITED_CONNECTOR -> "同花连张";
            case AX -> "A-x";
            case BLUFF_CATCHER -> "Bluff Catcher";
            case MONSTER -> "Monster";
        };
    }

    private String labelForDraw(DrawType drawType) {
        return switch (drawType) {
            case RANDOM, ANY -> "任意听牌";
            case NONE -> "无听牌";
            case FLUSH_DRAW -> "同花听牌";
            case STRAIGHT_DRAW -> "顺子听牌";
            case COMBO_DRAW -> "组合听牌";
            case BACKDOOR -> "后门潜力";
        };
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private String amountLabel(double value) {
        double rounded = round(value);
        return rounded == Math.rint(rounded) ? String.valueOf((long) rounded) : String.valueOf(rounded);
    }

    private String actionLabel(String verb, double requestedAmount, double effectiveStack) {
        double cappedAmount = Math.min(Math.max(0.0, requestedAmount), effectiveStack);
        if (cappedAmount >= effectiveStack) {
            return "All-in " + amountLabel(effectiveStack);
        }
        return verb + " " + amountLabel(cappedAmount);
    }

    private record BlindLevel(String label, double smallBlind, double bigBlind) {
        private static final BlindLevel DEFAULT = new BlindLevel("1/2", 1.0, 2.0);
    }

    private record TrainingConfig(
            SeatPosition heroPosition,
            int opponentCount,
            List<SeatPosition> requestedOpponents,
            Stage startStage,
            ScenarioType scenarioType,
            SeatPosition gtoOpponentPosition,
            RelativePosition relativePosition,
            HandRange handRange,
            HandType handType,
            DrawType drawType,
            int sessionHands,
            BlindLevel blindLevel,
            int totalStack
    ) {
    }

    private record CardBundle(Combo heroHand, Combo opponentHand, List<String> board) {
    }

    private record SpotEconomy(double potSize, double effectiveStack, double facingSize) {
    }

    private record ActionSet(List<ActionOptionView> options, DecisionMode mode) {
    }

    private record ActionProfile(ActionOptionView option, double frequency, double ev, double evGap) {
        ActionProfile(ActionOptionView option, double frequency, double ev) {
            this(option, frequency, ev, 0.0);
        }

        ActionProfile withGap(double gap) {
            return new ActionProfile(option, frequency, ev, gap);
        }
    }

    private static final class TrainerStats {
        private int handsPlayed;
        private int actionsTaken;
        private int actionScore;
        private final EnumMap<ScoreBand, Integer> counters = new EnumMap<>(ScoreBand.class);

        private TrainerStats() {
            for (ScoreBand band : ScoreBand.values()) {
                counters.put(band, 0);
            }
        }

        void apply(ScoreBand band, boolean handCompleted) {
            if (handCompleted) {
                handsPlayed++;
            }
            actionsTaken++;
            actionScore += band.points();
            counters.computeIfPresent(band, (ignored, count) -> count + 1);
        }

        StatsView toView(int totalHands) {
            int maxScore = Math.max(0, actionsTaken * 4);
            double rawScore = actionScore;
            int percent = maxScore == 0 ? 0 : (int) Math.round((rawScore / maxScore) * 100.0);
            return new StatsView(
                    handsPlayed,
                    actionsTaken,
                    percent,
                    roundScore(rawScore),
                    maxScore,
                    percent,
                    counters.get(ScoreBand.BEST),
                    counters.get(ScoreBand.CORRECT),
                    counters.get(ScoreBand.QUESTIONABLE),
                    counters.get(ScoreBand.ERROR),
                    counters.get(ScoreBand.BLUNDER)
            );
        }

        int handsPlayed() {
            return handsPlayed;
        }

        private double roundScore(double value) {
            return Math.round(value * 10.0) / 10.0;
        }
    }

    private static final class SessionState {
        private final String sessionId;
        private final TrainingConfig config;
        private final List<SpotState> spots;
        private final TrainerStats stats;
        private final Map<Integer, HandHistoryState> handHistories;
        private int currentIndex;
        private boolean awaitingNextHand;

        private SessionState(String sessionId, TrainingConfig config, List<SpotState> spots) {
            this.sessionId = sessionId;
            this.config = config;
            this.spots = spots;
            this.stats = new TrainerStats();
            this.handHistories = buildHandHistories(spots);
            this.currentIndex = 0;
            this.awaitingNextHand = false;
        }

        String sessionId() {
            return sessionId;
        }

        TrainingConfig config() {
            return config;
        }

        List<SpotState> spots() {
            return spots;
        }

        TrainerStats stats() {
            return stats;
        }

        List<HandHistoryView> handHistoryViews() {
            return handHistories.values().stream()
                    .map(HandHistoryState::toView)
                    .toList();
        }

        int currentIndex() {
            return currentIndex;
        }

        SpotState currentSpot() {
            return spots.get(currentIndex);
        }

        boolean awaitingNextHand() {
            return awaitingNextHand;
        }

        boolean completed() {
            return currentIndex >= spots.size();
        }

        void advance() {
            currentIndex = Math.min(currentIndex + 1, spots.size());
        }

        void advancePastHand(int handNumber) {
            while (currentIndex < spots.size() && spots.get(currentIndex).handNumber() == handNumber) {
                currentIndex++;
            }
        }

        void pauseForNextHand() {
            awaitingNextHand = true;
        }

        void continueToNextHand() {
            awaitingNextHand = false;
        }

        void recordAutomaticOperations(SpotState spot) {
            HandHistoryState history = handHistories.get(spot.handNumber());
            if (history != null) {
                history.recordAutomaticOperations(spot);
            }
        }

        void recordHeroOperation(SpotState spot, ActionProfile profile, ScoreBand band, FeedbackView feedback) {
            HandHistoryState history = handHistories.get(spot.handNumber());
            if (history != null) {
                history.recordHeroOperation(spot, profile, band, feedback);
            }
        }

        void completeHandByAllIn(SpotState spot) {
            HandHistoryState history = handHistories.get(spot.handNumber());
            if (history != null) {
                history.completeByAllIn(spot);
            }
        }

        void completeHandByFold(SpotState spot) {
            HandHistoryState history = handHistories.get(spot.handNumber());
            if (history != null) {
                history.completeByFold(spot);
            }
        }

        void completeHandByShowdown(SpotState spot) {
            HandHistoryState history = handHistories.get(spot.handNumber());
            if (history != null) {
                history.completeByShowdown(spot);
            }
        }

        private static Map<Integer, HandHistoryState> buildHandHistories(List<SpotState> spots) {
            Map<Integer, HandHistoryState> histories = new LinkedHashMap<>();
            for (SpotState spot : spots) {
                histories.computeIfAbsent(spot.handNumber(), ignored -> new HandHistoryState(spot));
                histories.get(spot.handNumber()).mergeSpot(spot);
            }
            return histories;
        }
    }

    private static final class HandHistoryState {
        private final int handNumber;
        private final String heroPosition;
        private final String gtoOpponentPosition;
        private final String heroHand;
        private final String opponentHand;
        private final String scenarioType;
        private List<String> finalBoard = List.of();
        private List<String> revealedBoard = List.of();
        private final List<OperationState> operations = new ArrayList<>();
        private final Set<String> recordedAutoLines = new LinkedHashSet<>();
        private final Set<String> recordedBoardStages = new LinkedHashSet<>();
        private int actionScore;
        private int heroDecisionCount;
        private int nextSequence = 1;
        private boolean completed;
        private String outcomeLabel = "未结束";

        private HandHistoryState(SpotState spot) {
            this.handNumber = spot.handNumber();
            this.heroPosition = spot.heroPosition().name();
            this.gtoOpponentPosition = spot.focusOpponentPosition().name();
            this.heroHand = spot.heroHand().text();
            this.opponentHand = spot.opponentHand().text();
            this.scenarioType = labelScenarioForHistory(spot.scenario());
        }

        void mergeSpot(SpotState spot) {
            if (spot.board().size() > finalBoard.size()) {
                finalBoard = List.copyOf(spot.board());
            }
        }

        void recordAutomaticOperations(SpotState spot) {
            revealBoard(spot.board());
            for (String line : spot.actionHistory()) {
                if (!isRecordableLine(line)) {
                    continue;
                }
                String key = spot.handNumber() + "|" + line;
                if (!recordedAutoLines.add(key)) {
                    continue;
                }
                rememberBoardStage(line);
                operations.add(OperationState.automatic(nextSequence++, spot.stage(), actorFor(line, spot), positionFor(line, spot), actionFor(line), line));
            }
        }

        void recordHeroOperation(SpotState spot, ActionProfile profile, ScoreBand band, FeedbackView feedback) {
            revealBoard(spot.board());
            actionScore += band.points();
            heroDecisionCount++;
            String detail = spot.heroPosition() + " chooses " + profile.option().label();
            operations.add(OperationState.hero(
                    nextSequence++,
                    spot.stage(),
                    spot.heroPosition().name(),
                    profile.option().label(),
                    detail,
                    band.label(),
                    band.points(),
                    feedback.bestAction(),
                    feedback.explanation()
            ));
        }

        void completeByAllIn(SpotState spot) {
            if (completed) {
                return;
            }
            recordAutomaticOpponentCall(spot);
            revealBoard(finalBoard);
            recordBoardRunout();
            ShowdownResult showdown = showdownResult(spot);
            completed = true;
            outcomeLabel = showdown.label();
            operations.add(OperationState.system(
                    nextSequence++,
                    labelStageForHistory(spot.stage()),
                    showdown.action(),
                    showdown.detail()
            ));
        }

        void completeByFold(SpotState spot) {
            if (completed) {
                return;
            }
            revealBoard(spot.board());
            finalBoard = List.copyOf(revealedBoard);
            completed = true;
            outcomeLabel = "Hero 弃牌，本手告负";
            operations.add(OperationState.system(
                    nextSequence++,
                    labelStageForHistory(spot.stage()),
                    "弃牌结束",
                    "Hero 弃牌，GTO 赢下当前底池。"
            ));
        }

        void completeByShowdown(SpotState spot) {
            if (completed) {
                return;
            }
            revealBoard(finalBoard.isEmpty() ? spot.board() : finalBoard);
            recordBoardRunout();
            ShowdownResult showdown = showdownResult(spot);
            completed = true;
            outcomeLabel = showdown.label();
            operations.add(OperationState.system(
                    nextSequence++,
                    labelStageForHistory(spot.stage()),
                    showdown.action(),
                    showdown.detail()
            ));
        }

        HandHistoryView toView() {
            int maxScore = Math.max(0, heroDecisionCount * 4);
            double handScore = maxScore == 0 ? 0.0 : Math.round((actionScore * 100.0) / maxScore);
            return new HandHistoryView(
                    handNumber,
                    completed,
                    heroPosition,
                    gtoOpponentPosition,
                    heroHand,
                    opponentHand,
                    completed ? finalBoard : revealedBoard,
                    scenarioType,
                    handScore,
                    outcomeLabel,
                    completed ? "已完成 · 本手得分 " + formatPercent(handScore) : "进行中 · 当前得分 " + formatPercent(handScore),
                    behaviorAnalysis(),
                    operations.stream().map(OperationState::toView).toList()
            );
        }

        private String behaviorAnalysis() {
            List<OperationState> heroActions = operations.stream()
                    .filter(operation -> "HERO".equals(operation.actor()))
                    .toList();
            if (heroActions.isEmpty()) {
                return "本手还没有 Hero 决策。";
            }
            long errors = heroActions.stream().filter(operation -> operation.deltaScore() <= ScoreBand.ERROR.points()).count();
            long best = heroActions.stream().filter(operation -> operation.deltaScore() == ScoreBand.BEST.points()).count();
            if (errors > 0) {
                return "本手有 " + errors + " 次低分决策，优先复盘这些节点的推荐动作和EV差距。";
            }
            if (best > 0) {
                return "本手有 " + best + " 次最佳行动，整体策略贴近当前模拟GTO主线。";
            }
            return "本手没有明显巨大错误，但存在低频或混合动作，适合关注每条街的推荐主动作。";
        }

        private void revealBoard(List<String> board) {
            if (board != null && board.size() > revealedBoard.size()) {
                revealedBoard = List.copyOf(board);
            }
        }

        private void recordAutomaticOpponentCall(SpotState spot) {
            String detail = spot.focusOpponentPosition().name() + " 跟注 Hero 的 all-in";
            operations.add(OperationState.automatic(
                    nextSequence++,
                    spot.stage(),
                    "GTO",
                    spot.focusOpponentPosition().name(),
                    "跟注 all-in",
                    detail
            ));
        }

        private void recordBoardRunout() {
            if (finalBoard.size() >= 3) {
                recordBoardStage("FLOP", "Flop " + String.join(" ", finalBoard.subList(0, 3)));
            }
            if (finalBoard.size() >= 4) {
                recordBoardStage("TURN", "Turn " + finalBoard.get(3));
            }
            if (finalBoard.size() >= 5) {
                recordBoardStage("RIVER", "River " + finalBoard.get(4));
            }
        }

        private void recordBoardStage(String stageKey, String detail) {
            if (!recordedBoardStages.add(stageKey)) {
                return;
            }
            Stage stage = switch (stageKey) {
                case "FLOP" -> Stage.FLOP;
                case "TURN" -> Stage.TURN;
                case "RIVER" -> Stage.RIVER;
                default -> Stage.RANDOM;
            };
            operations.add(OperationState.automatic(
                    nextSequence++,
                    stage,
                    "BOARD",
                    "",
                    actionFor(detail),
                    detail
            ));
        }

        private void rememberBoardStage(String line) {
            if (line.startsWith("Flop")) {
                recordedBoardStages.add("FLOP");
            } else if (line.startsWith("Turn")) {
                recordedBoardStages.add("TURN");
            } else if (line.startsWith("River")) {
                recordedBoardStages.add("RIVER");
            }
        }

        private ShowdownResult showdownResult(SpotState spot) {
            if (finalBoard.size() < 5) {
                return new ShowdownResult("摊牌条件不足", "牌面未完整发完，结果未能计算。");
            }
            Card[] boardCards = finalBoard.stream().map(Card::parse).toArray(Card[]::new);
            int compare = com.solvergto.poker.HandEvaluator.compare(
                    Combo.parse(heroHand).toSeven(boardCards),
                    Combo.parse(opponentHand).toSeven(boardCards)
            );
            if (compare > 0) {
                return new ShowdownResult("Hero 赢下摊牌", "双方摊牌，Hero 取得胜利。");
            }
            if (compare < 0) {
                return new ShowdownResult("GTO 赢下摊牌", "双方摊牌，GTO 对手赢下本手。");
            }
            return new ShowdownResult("双方平分底池", "双方摊牌后牌力相同，本手平分底池。");
        }

        private static boolean isRecordableLine(String line) {
            return line != null
                    && !line.isBlank()
                    && !line.contains("to act")
                    && !line.contains("continue to")
                    && !"GTO opponent action is auto-resolved".equals(line);
        }

        private static String actorFor(String line, SpotState spot) {
            if (line.startsWith(spot.heroPosition().name())) {
                return "HERO";
            }
            if (line.startsWith(spot.focusOpponentPosition().name())) {
                return "GTO";
            }
            if (line.startsWith("Flop") || line.startsWith("Turn") || line.startsWith("River")) {
                return "BOARD";
            }
            return "SYSTEM";
        }

        private static String positionFor(String line, SpotState spot) {
            if (line.startsWith(spot.heroPosition().name())) {
                return spot.heroPosition().name();
            }
            if (line.startsWith(spot.focusOpponentPosition().name())) {
                return spot.focusOpponentPosition().name();
            }
            return "";
        }

        private static String actionFor(String line) {
            if (line.startsWith("Flop")) {
                return "发翻牌";
            }
            if (line.startsWith("Turn")) {
                return "发转牌";
            }
            if (line.startsWith("River")) {
                return "发河牌";
            }
            int firstSpace = line.indexOf(' ');
            return firstSpace > 0 ? line.substring(firstSpace + 1) : line;
        }

        private static String formatPercent(double value) {
            return Math.round(value) + "%";
        }
    }

    private record OperationState(
            int sequence,
            String stage,
            String actor,
            String position,
            String action,
            String detail,
            boolean automatic,
            String scoreCategory,
            int deltaScore,
            String bestAction,
            String analysis
    ) {
        static OperationState automatic(int sequence, Stage stage, String actor, String position, String action, String detail) {
            return new OperationState(sequence, labelStageForHistory(stage), actor, position, action, detail, true, "", 0, "", "");
        }

        static OperationState hero(int sequence, Stage stage, String position, String action, String detail,
                                   String scoreCategory, int deltaScore, String bestAction, String analysis) {
            return new OperationState(sequence, labelStageForHistory(stage), "HERO", position, action, detail, false, scoreCategory, deltaScore, bestAction, analysis);
        }

        static OperationState system(int sequence, String stage, String action, String detail) {
            return new OperationState(sequence, stage, "SYSTEM", "", action, detail, true, "", 0, "", "");
        }

        OperationView toView() {
            return new OperationView(sequence, stage, actor, position, action, detail, automatic, scoreCategory, deltaScore, bestAction, analysis);
        }
    }

    private record ShowdownResult(String label, String detail) {
        String action() {
            return label;
        }
    }

    private record SpotState(
            String spotId,
            int handNumber,
            Stage stage,
            ScenarioType scenario,
            DecisionMode decisionMode,
            SeatPosition heroPosition,
            SeatPosition focusOpponentPosition,
            List<SeatPosition> activeOpponents,
            Combo heroHand,
            Combo opponentHand,
            List<String> board,
            double potSize,
            double effectiveStack,
            double facingSize,
            List<String> actionHistory,
            String handDescriptor,
            String drawDescriptor,
            Map<String, ActionProfile> profileByCode
    ) {
        ActionProfile bestProfile() {
            return profileByCode.values().stream()
                    .max((left, right) -> Double.compare(left.ev(), right.ev()))
                    .orElseThrow();
        }
    }
}

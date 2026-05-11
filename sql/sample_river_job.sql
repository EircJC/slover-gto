USE `solver-gto`;

INSERT INTO solver_job (
    id, job_name, game_variant, street, board_cards, pot_size, effective_stack,
    hero_seat, first_actor_seat, iterations, status, notes
) VALUES (
    1, 'sample-river-spot', 'HUNL_RIVER', 'RIVER', 'AsKd7h2cTc',
    150.00, 850.00, 0, 0, 1200, 'PENDING',
    'Sample heads-up river spot'
)
ON DUPLICATE KEY UPDATE
    job_name = VALUES(job_name),
    board_cards = VALUES(board_cards),
    pot_size = VALUES(pot_size),
    effective_stack = VALUES(effective_stack),
    hero_seat = VALUES(hero_seat),
    first_actor_seat = VALUES(first_actor_seat),
    iterations = VALUES(iterations),
    status = VALUES(status),
    notes = VALUES(notes);

DELETE FROM solver_player_range WHERE job_id = 1;
DELETE FROM solver_tree_node WHERE job_id = 1;

INSERT INTO solver_player_range (job_id, seat, combo_cards, weight) VALUES
(1, 0, 'AhKh', 1.0),
(1, 0, 'QhJh', 1.0),
(1, 0, '9s9d', 1.0),
(1, 0, 'AcQd', 1.0),
(1, 1, 'AdQh', 1.0),
(1, 1, 'KsQs', 1.0),
(1, 1, 'JhTh', 1.0),
(1, 1, '8c8s', 1.0);

INSERT INTO solver_tree_node (
    job_id, node_key, parent_node_key, actor_seat, action_type, amount_to_add, terminal_type, sort_order
) VALUES
(1, 'root', NULL, 0, 'ROOT', 0.00, NULL, 0),
(1, 'root_check', 'root', 1, 'CHECK', 0.00, NULL, 0),
(1, 'root_check_check', 'root_check', NULL, 'CHECK', 0.00, 'SHOWDOWN', 0),
(1, 'root_check_bet75', 'root_check', 0, 'BET', 75.00, NULL, 1),
(1, 'root_check_bet75_fold', 'root_check_bet75', NULL, 'FOLD', 0.00, 'FOLD', 0),
(1, 'root_check_bet75_call', 'root_check_bet75', NULL, 'CALL', 75.00, 'SHOWDOWN', 1),
(1, 'root_bet75', 'root', 1, 'BET', 75.00, NULL, 1),
(1, 'root_bet75_fold', 'root_bet75', NULL, 'FOLD', 0.00, 'FOLD', 0),
(1, 'root_bet75_call', 'root_bet75', NULL, 'CALL', 75.00, 'SHOWDOWN', 1),
(1, 'root_bet75_raise225', 'root_bet75', 0, 'RAISE', 225.00, NULL, 2),
(1, 'root_bet75_raise225_fold', 'root_bet75_raise225', NULL, 'FOLD', 0.00, 'FOLD', 0),
(1, 'root_bet75_raise225_call', 'root_bet75_raise225', NULL, 'CALL', 225.00, 'SHOWDOWN', 1);

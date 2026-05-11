package com.solvergto.service;

import com.solvergto.auth.AuthModels.PlayerView;
import com.solvergto.db.mapper.PlayerInsertCommand;
import com.solvergto.db.mapper.PlayerMapper;
import com.solvergto.model.PlayerRecord;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Locale;

@Service
public class PlayerService {
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private final PlayerMapper playerMapper;

    public PlayerService(PlayerMapper playerMapper) {
        this.playerMapper = playerMapper;
    }

    public PlayerView register(String username, String password, String displayName, String mode) {
        String normalizedUsername = normalizeUsername(username);
        validatePassword(password);
        String safeDisplayName = displayName == null || displayName.isBlank() ? normalizedUsername : displayName.trim();
        try {
            PlayerInsertCommand command = new PlayerInsertCommand(normalizedUsername, hashPassword(password), safeDisplayName);
            playerMapper.insert(command);
            return new PlayerView(command.getId(), normalizedUsername, safeDisplayName, normalizeMode(mode));
        } catch (DuplicateKeyException exception) {
            throw new IllegalArgumentException("用户名已存在");
        }
    }

    public PlayerView login(String username, String password, String mode) {
        String normalizedUsername = normalizeUsername(username);
        PlayerRecord player = playerMapper.findByUsername(normalizedUsername);
        if (player == null || !"ACTIVE".equalsIgnoreCase(player.status()) || !verifyPassword(password, player.passwordHash())) {
            throw new IllegalArgumentException("用户名或密码错误");
        }
        playerMapper.markLogin(player.id());
        return new PlayerView(player.id(), player.username(), player.displayName(), normalizeMode(mode));
    }

    public PlayerView getPlayer(long playerId, String mode) {
        PlayerRecord player = playerMapper.findById(playerId);
        if (player == null) {
            throw new IllegalArgumentException("玩家不存在");
        }
        return new PlayerView(player.id(), player.username(), player.displayName(), normalizeMode(mode));
    }

    public String normalizeMode(String mode) {
        if (mode == null || mode.isBlank()) {
            return "CASH";
        }
        String normalized = mode.trim().toUpperCase(Locale.ROOT);
        if (!"CASH".equals(normalized) && !"MTT".equals(normalized)) {
            throw new IllegalArgumentException("训练模式只支持CASH或MTT");
        }
        return normalized;
    }

    private String normalizeUsername(String username) {
        if (username == null || username.isBlank()) {
            throw new IllegalArgumentException("用户名不能为空");
        }
        String normalized = username.trim().toLowerCase(Locale.ROOT);
        if (normalized.length() < 3 || normalized.length() > 64) {
            throw new IllegalArgumentException("用户名长度必须在3到64之间");
        }
        return normalized;
    }

    private void validatePassword(String password) {
        if (password == null || password.length() < 6) {
            throw new IllegalArgumentException("密码至少需要6位");
        }
    }

    private String hashPassword(String password) {
        try {
            byte[] salt = new byte[16];
            SECURE_RANDOM.nextBytes(salt);
            byte[] hash = pbkdf2(password.toCharArray(), salt);
            return "pbkdf2$" + Base64.getEncoder().encodeToString(salt) + "$" + Base64.getEncoder().encodeToString(hash);
        } catch (Exception exception) {
            throw new IllegalStateException("密码加密失败", exception);
        }
    }

    private boolean verifyPassword(String password, String storedHash) {
        try {
            if (password == null || storedHash == null || !storedHash.startsWith("pbkdf2$")) {
                return false;
            }
            String[] parts = storedHash.split("\\$");
            byte[] salt = Base64.getDecoder().decode(parts[1]);
            byte[] expected = Base64.getDecoder().decode(parts[2]);
            byte[] actual = pbkdf2(password.toCharArray(), salt);
            if (actual.length != expected.length) {
                return false;
            }
            int diff = 0;
            for (int i = 0; i < actual.length; i++) {
                diff |= actual[i] ^ expected[i];
            }
            return diff == 0;
        } catch (Exception exception) {
            return false;
        }
    }

    private byte[] pbkdf2(char[] password, byte[] salt) throws Exception {
        PBEKeySpec spec = new PBEKeySpec(password, salt, 120_000, 256);
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
    }
}

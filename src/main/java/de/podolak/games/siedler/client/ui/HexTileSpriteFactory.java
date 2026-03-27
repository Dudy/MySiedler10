package de.podolak.games.siedler.client.ui;

import de.podolak.games.siedler.shared.model.TerrainType;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Polygon;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.util.EnumMap;
import java.util.Map;
import java.util.Random;

final class HexTileSpriteFactory {
    private static final int VARIANT_COUNT = 6;

    private final int hexSize;
    private final int spriteWidth;
    private final int spriteHeight;
    private final int centerX;
    private final int centerY;
    private final Polygon localHex;
    private final Map<TerrainType, BufferedImage[]> spriteCache = new EnumMap<>(TerrainType.class);

    HexTileSpriteFactory(int hexSize, double hexWidth, double hexHeight) {
        this.hexSize = hexSize;
        this.spriteWidth = (int) Math.ceil(hexWidth) + 6;
        this.spriteHeight = (int) Math.ceil(hexHeight) + 6;
        this.centerX = spriteWidth / 2;
        this.centerY = spriteHeight / 2;
        this.localHex = createLocalHex();
    }

    void paintTile(Graphics2D graphics2D, TerrainType terrainType, int tileX, int tileY, Point center) {
        BufferedImage sprite = spriteFor(terrainType, tileX, tileY);
        graphics2D.drawImage(sprite, center.x - centerX, center.y - centerY, null);
    }

    private BufferedImage spriteFor(TerrainType terrainType, int tileX, int tileY) {
        BufferedImage[] variants = spriteCache.computeIfAbsent(terrainType, this::createVariants);
        int variantIndex = Math.floorMod(tileX * 37 + tileY * 19 + tileX * tileY * 7, variants.length);
        return variants[variantIndex];
    }

    private BufferedImage[] createVariants(TerrainType terrainType) {
        BufferedImage[] variants = new BufferedImage[VARIANT_COUNT];
        for (int variant = 0; variant < VARIANT_COUNT; variant++) {
            variants[variant] = createSprite(terrainType, variant);
        }
        return variants;
    }

    private BufferedImage createSprite(TerrainType terrainType, int variant) {
        BufferedImage image = new BufferedImage(spriteWidth, spriteHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        graphics.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

        Random random = new Random(terrainType.ordinal() * 10_003L + variant * 1_993L + 17L);
        graphics.setClip(localHex);
        paintBaseTerrain(graphics, terrainType, random);
        paintTerrainDetails(graphics, terrainType, random);
        paintSoftLight(graphics, terrainType);
        graphics.setClip(null);
        paintTerrainBorder(graphics, terrainType);
        graphics.dispose();
        return image;
    }

    private void paintBaseTerrain(Graphics2D graphics, TerrainType terrainType, Random random) {
        Color topColor;
        Color bottomColor;
        switch (terrainType) {
            case GRASSLAND -> {
                topColor = new Color(167, 194, 93);
                bottomColor = new Color(123, 157, 73);
            }
            case FOREST -> {
                topColor = new Color(136, 172, 84);
                bottomColor = new Color(93, 128, 61);
            }
            case MOUNTAIN -> {
                topColor = new Color(156, 187, 93);
                bottomColor = new Color(101, 137, 63);
            }
            case WATER -> {
                topColor = new Color(106, 188, 233);
                bottomColor = new Color(60, 129, 199);
            }
            case SAND -> {
                topColor = new Color(226, 205, 137);
                bottomColor = new Color(194, 165, 103);
            }
            default -> throw new IllegalStateException("Unexpected terrain type: " + terrainType);
        }

        int gradientShift = random.nextInt(6) - 3;
        graphics.setPaint(new GradientPaint(
                0,
                gradientShift,
                topColor,
                spriteWidth,
                spriteHeight,
                bottomColor
        ));
        graphics.fillPolygon(localHex);

        Color grainColor = terrainType == TerrainType.WATER
                ? new Color(255, 255, 255, 16)
                : new Color(255, 243, 210, 15);
        for (int i = 0; i < 60; i++) {
            int x = 2 + random.nextInt(Math.max(1, spriteWidth - 4));
            int y = 2 + random.nextInt(Math.max(1, spriteHeight - 4));
            int width = 1 + random.nextInt(3);
            int height = 1 + random.nextInt(3);
            graphics.setColor(grainColor);
            graphics.fillOval(x, y, width, height);
        }
    }

    private void paintTerrainDetails(Graphics2D graphics, TerrainType terrainType, Random random) {
        switch (terrainType) {
            case GRASSLAND -> paintGrassland(graphics, random);
            case FOREST -> paintForest(graphics, random);
            case MOUNTAIN -> paintMountains(graphics, random);
            case WATER -> paintWater(graphics, random);
            case SAND -> paintSand(graphics, random);
        }
    }

    private void paintGrassland(Graphics2D graphics, Random random) {
        for (int i = 0; i < 9; i++) {
            int width = 4 + random.nextInt(9);
            int height = 2 + random.nextInt(5);
            int x = 2 + random.nextInt(Math.max(1, spriteWidth - width - 4));
            int y = 4 + random.nextInt(Math.max(1, spriteHeight - height - 8));
            graphics.setColor(new Color(199, 214, 116, 42 + random.nextInt(38)));
            graphics.fill(new Ellipse2D.Float(x, y, width, height));
        }

        for (int i = 0; i < 8; i++) {
            int baseX = 4 + random.nextInt(Math.max(1, spriteWidth - 8));
            int baseY = 5 + random.nextInt(Math.max(1, spriteHeight - 10));
            graphics.setStroke(new BasicStroke(1.1f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            graphics.setColor(new Color(136, 171, 72, 145));
            graphics.drawLine(baseX, baseY, baseX + 3, baseY - 1);
            graphics.setColor(new Color(215, 233, 133, 110));
            graphics.drawLine(baseX, baseY, baseX + 2, baseY - 2);
        }
    }

    private void paintForest(Graphics2D graphics, Random random) {
        paintGrassland(graphics, random);
        for (int i = 0; i < 18; i++) {
            int radius = 4 + random.nextInt(5);
            int x = 1 + random.nextInt(Math.max(1, spriteWidth - radius - 2));
            int y = 4 + random.nextInt(Math.max(1, spriteHeight - radius - 8));

            graphics.setColor(new Color(31, 74, 36, 120));
            graphics.fillOval(x + 1, y + 2, radius, radius - 1);
            graphics.setColor(new Color(26, 97, 41, 220));
            graphics.fillOval(x, y, radius, radius);
            graphics.setColor(new Color(67, 143, 70, 160));
            graphics.fillOval(x + 1, y, Math.max(2, radius - 2), Math.max(2, radius - 2));
        }
    }

    private void paintMountains(Graphics2D graphics, Random random) {
        paintGrassland(graphics, random);

        int mountainCount = 2 + random.nextInt(2);
        for (int i = 0; i < mountainCount; i++) {
            float peakX = spriteWidth * (0.26f + i * 0.26f) + random.nextFloat() * 5f;
            float peakY = 5f + random.nextFloat() * 4f;
            float baseY = spriteHeight * (0.52f + random.nextFloat() * 0.12f);
            float halfWidth = 8f + random.nextFloat() * 7f;

            Path2D.Float body = new Path2D.Float();
            body.moveTo(peakX, peakY);
            body.curveTo(peakX + halfWidth * 0.36f, peakY + 8f, peakX + halfWidth * 0.78f, baseY - 7f, peakX + halfWidth, baseY);
            body.lineTo(peakX, baseY + 4f);
            body.lineTo(peakX - halfWidth, baseY);
            body.curveTo(peakX - halfWidth * 0.76f, baseY - 7f, peakX - halfWidth * 0.35f, peakY + 8f, peakX, peakY);

            graphics.setColor(new Color(95, 129, 53, 190));
            graphics.fill(body);

            Path2D.Float leftSlope = new Path2D.Float();
            leftSlope.moveTo(peakX, peakY + 1f);
            leftSlope.lineTo(peakX - halfWidth * 0.72f, baseY);
            leftSlope.lineTo(peakX, baseY + 4f);
            leftSlope.closePath();
            graphics.setColor(new Color(147, 199, 83, 205));
            graphics.fill(leftSlope);

            Path2D.Float rockyCap = new Path2D.Float();
            rockyCap.moveTo(peakX, peakY - 1f);
            rockyCap.lineTo(peakX + halfWidth * 0.25f, peakY + 7f);
            rockyCap.lineTo(peakX - halfWidth * 0.24f, peakY + 10f);
            rockyCap.lineTo(peakX - halfWidth * 0.4f, peakY + 5f);
            rockyCap.closePath();
            graphics.setColor(new Color(114, 78, 50, 235));
            graphics.fill(rockyCap);

            graphics.setStroke(new BasicStroke(1.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            graphics.setColor(new Color(82, 60, 37, 120));
            graphics.draw(body);
        }

        for (int i = 0; i < 3; i++) {
            graphics.setColor(new Color(128, 89, 49, 130));
            graphics.fillOval(
                    8 + random.nextInt(Math.max(1, spriteWidth - 18)),
                    10 + random.nextInt(Math.max(1, spriteHeight - 18)),
                    6 + random.nextInt(7),
                    2 + random.nextInt(3)
            );
        }
    }

    private void paintWater(Graphics2D graphics, Random random) {
        graphics.setStroke(new BasicStroke(1.1f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (int i = 0; i < 10; i++) {
            int width = 4 + random.nextInt(7);
            int x = 3 + random.nextInt(Math.max(1, spriteWidth - width - 6));
            int y = 4 + random.nextInt(Math.max(1, spriteHeight - 8));
            graphics.setColor(new Color(219, 248, 255, 110 + random.nextInt(50)));
            graphics.drawArc(x, y, width, 3 + random.nextInt(2), 10, 160);
        }

        for (int i = 0; i < 5; i++) {
            int x = 4 + random.nextInt(Math.max(1, spriteWidth - 8));
            int y = 5 + random.nextInt(Math.max(1, spriteHeight - 10));
            graphics.setColor(new Color(255, 255, 255, 130));
            graphics.fillOval(x, y, 2, 2);
        }
    }

    private void paintSand(Graphics2D graphics, Random random) {
        for (int i = 0; i < 8; i++) {
            int width = 8 + random.nextInt(9);
            int x = 2 + random.nextInt(Math.max(1, spriteWidth - width - 4));
            int y = 5 + random.nextInt(Math.max(1, spriteHeight - 10));
            graphics.setColor(new Color(255, 235, 170, 55 + random.nextInt(25)));
            graphics.fill(new Ellipse2D.Float(x, y, width, 3 + random.nextInt(2)));
        }

        graphics.setStroke(new BasicStroke(1.1f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (int i = 0; i < 7; i++) {
            int width = 7 + random.nextInt(8);
            int x = 2 + random.nextInt(Math.max(1, spriteWidth - width - 4));
            int y = 6 + random.nextInt(Math.max(1, spriteHeight - 10));
            graphics.setColor(new Color(181, 145, 82, 90));
            graphics.drawArc(x, y, width, 5, 15, 140);
        }
    }

    private void paintSoftLight(Graphics2D graphics, TerrainType terrainType) {
        Color centerGlow = switch (terrainType) {
            case WATER -> new Color(255, 255, 255, 80);
            case SAND -> new Color(255, 248, 223, 82);
            default -> new Color(255, 255, 220, 55);
        };
        Color edgeShade = switch (terrainType) {
            case WATER -> new Color(16, 53, 103, 88);
            case SAND -> new Color(119, 93, 42, 72);
            default -> new Color(33, 57, 20, 72);
        };

        graphics.setPaint(new RadialGradientPaint(
                centerX - 4f,
                centerY - 6f,
                hexSize * 1.2f,
                new float[]{0f, 0.65f, 1f},
                new Color[]{centerGlow, new Color(centerGlow.getRed(), centerGlow.getGreen(), centerGlow.getBlue(), 12), edgeShade}
        ));
        graphics.fillPolygon(localHex);
    }

    private void paintTerrainBorder(Graphics2D graphics, TerrainType terrainType) {
        Color outerBorder = terrainType == TerrainType.WATER
                ? new Color(125, 94, 61, 150)
                : new Color(109, 80, 49, 160);
        Color innerBorder = terrainType == TerrainType.WATER
                ? new Color(232, 245, 255, 120)
                : new Color(41, 79, 31, 110);

        graphics.setStroke(new BasicStroke(2.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        graphics.setColor(outerBorder);
        graphics.drawPolygon(localHex);

        graphics.setStroke(new BasicStroke(1f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        graphics.setColor(innerBorder);
        graphics.drawPolygon(localHex);

        int[] xs = localHex.xpoints;
        int[] ys = localHex.ypoints;
        graphics.setStroke(new BasicStroke(1.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        graphics.setColor(new Color(255, 255, 255, 90));
        graphics.drawLine(xs[4], ys[4], xs[5], ys[5]);
        graphics.drawLine(xs[5], ys[5], xs[0], ys[0]);
        graphics.setColor(new Color(92, 58, 31, 130));
        graphics.drawLine(xs[1], ys[1], xs[2], ys[2]);
        graphics.drawLine(xs[2], ys[2], xs[3], ys[3]);
    }

    private Polygon createLocalHex() {
        int[] xs = new int[6];
        int[] ys = new int[6];
        for (int i = 0; i < 6; i++) {
            double angle = Math.toRadians(60 * i - 30);
            xs[i] = (int) Math.round(centerX + hexSize * Math.cos(angle));
            ys[i] = (int) Math.round(centerY + hexSize * Math.sin(angle));
        }
        return new Polygon(xs, ys, 6);
    }
}

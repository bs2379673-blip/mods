package com.annis.npc.client.importer;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/** Lector de BVH (captura de movimiento: Mixamo, CMU, Bandai Namco, Rokoko, Blender...). */
final class BvhReader {
    private static final class Joint {
        String name;
        Joint parent;
        double[] offset = V.v(0, 0, 0);
        String[] channels = new String[0];
        int start;
    }

    private final List<Joint> joints = new ArrayList<>();
    private double[][] data;
    double frameTime;
    private final Map<String, String> roles = new HashMap<>();

    private static final String[][] ROLE_PATTERNS = {
            {"hips", "^hips$", "pelvis", "hip$", "root$"},
            {"neck", "neck"},
            {"head", "^head$", "head"},
            {"armL", "upperarm_?l$", "leftarm$", "l_?upperarm", "left_?upper_?arm", "^arm_?l$", "leftshoulder"},
            {"armR", "upperarm_?r$", "rightarm$", "r_?upperarm", "right_?upper_?arm", "^arm_?r$", "rightshoulder"},
            {"handL", "hand_?l$", "lefthand$", "l_?hand$", "left_?hand", "leftwrist", "wrist_?l"},
            {"handR", "hand_?r$", "righthand$", "r_?hand$", "right_?hand", "rightwrist", "wrist_?r"},
            {"legL", "upperleg_?l$", "leftupleg$", "l_?thigh", "left_?thigh", "lefthip", "thigh_?l", "left_?up_?leg"},
            {"legR", "upperleg_?r$", "rightupleg$", "r_?thigh", "right_?thigh", "righthip", "thigh_?r", "right_?up_?leg"},
            {"elbowL", "lowerarm_?l$", "leftforearm$", "l_?forearm", "forearm_?l", "left_?elbow", "elbow_?l", "left_?fore_?arm"},
            {"elbowR", "lowerarm_?r$", "rightforearm$", "r_?forearm", "forearm_?r", "right_?elbow", "elbow_?r", "right_?fore_?arm"},
            {"kneeL", "lowerleg_?l$", "leftleg$", "l_?calf", "calf_?l", "left_?knee", "knee_?l", "l_?shin", "left_?shin"},
            {"kneeR", "lowerleg_?r$", "rightleg$", "r_?calf", "calf_?r", "right_?knee", "knee_?r", "r_?shin", "right_?shin"},
            {"footL", "foot_?l$", "leftfoot$", "l_?foot$", "left_?ankle", "ankle_?l", "left_?foot"},
            {"footR", "foot_?r$", "rightfoot$", "r_?foot$", "right_?ankle", "ankle_?r", "right_?foot"},
            {"toeL", "toes?_?l$", "lefttoe", "left_?toe", "l_?toe"},
            {"toeR", "toes?_?r$", "righttoe", "right_?toe", "r_?toe"},
    };

    static BvhReader read(Path file) throws Exception {
        BvhReader r = new BvhReader();
        r.parse(Files.readString(file));
        return r;
    }

    private void parse(String text) {
        String[] t = text.trim().split("\\s+");
        int i = 0;
        int channelsTotal = 0;
        List<Joint> stack = new ArrayList<>();
        Joint cur = null;
        while (i < t.length) {
            String tok = t[i];
            switch (tok) {
                case "ROOT", "JOINT" -> {
                    Joint j = new Joint();
                    j.name = t[i + 1];
                    j.parent = stack.isEmpty() ? null : stack.get(stack.size() - 1);
                    joints.add(j);
                    cur = j;
                    i += 2;
                }
                case "End" -> {
                    // End Site { OFFSET x y z }
                    i += 2;
                    int depth = 0;
                    do {
                        if (t[i].equals("{")) {
                            depth++;
                        } else if (t[i].equals("}")) {
                            depth--;
                        }
                        i++;
                    } while (depth > 0 && i < t.length);
                }
                case "{" -> {
                    stack.add(cur);
                    i++;
                }
                case "}" -> {
                    stack.remove(stack.size() - 1);
                    i++;
                }
                case "OFFSET" -> {
                    cur.offset = V.v(Double.parseDouble(t[i + 1]), Double.parseDouble(t[i + 2]), Double.parseDouble(t[i + 3]));
                    i += 4;
                }
                case "CHANNELS" -> {
                    int n = Integer.parseInt(t[i + 1]);
                    cur.channels = new String[n];
                    for (int k = 0; k < n; k++) {
                        cur.channels[k] = t[i + 2 + k];
                    }
                    cur.start = channelsTotal;
                    channelsTotal += n;
                    i += 2 + n;
                }
                case "MOTION" -> {
                    int frames = Integer.parseInt(t[i + 2]);
                    this.frameTime = Double.parseDouble(t[i + 5]);
                    i += 6;
                    this.data = new double[frames][channelsTotal];
                    for (int f = 0; f < frames; f++) {
                        for (int c = 0; c < channelsTotal; c++) {
                            this.data[f][c] = Double.parseDouble(t[i++]);
                        }
                    }
                    mapRoles();
                    return;
                }
                default -> i++;
            }
        }
        throw new IllegalArgumentException("BVH sin seccion MOTION");
    }

    private void mapRoles() {
        for (String[] role : ROLE_PATTERNS) {
            outer:
            for (int p = 1; p < role.length; p++) {
                Pattern pat = Pattern.compile(role[p]);
                for (Joint j : joints) {
                    String low = j.name.replaceAll("^.*:", "").toLowerCase(Locale.ROOT);
                    if (pat.matcher(low).find()) {
                        roles.put(role[0], j.name);
                        break outer;
                    }
                }
            }
        }
        for (String need : new String[]{"head", "armL", "armR", "handL", "handR", "legL", "legR", "footL", "footR"}) {
            if (!roles.containsKey(need)) {
                throw new IllegalArgumentException("No se encontro el hueso '" + need + "' en el BVH");
            }
        }
    }

    int frames() {
        return data.length;
    }

    Pose pose(int f) {
        double[] row = data[f];
        Map<String, double[]> gp = new HashMap<>();
        Map<String, double[]> gr = new HashMap<>();
        for (Joint j : joints) {
            double[] tr = j.offset.clone();
            double[] r = V.identity();
            for (int k = 0; k < j.channels.length; k++) {
                String c = j.channels[k];
                double val = row[j.start + k];
                if (c.endsWith("position")) {
                    tr["XYZ".indexOf(Character.toUpperCase(c.charAt(0)))] = val;
                }
            }
            for (int k = 0; k < j.channels.length; k++) {
                String c = j.channels[k];
                if (c.endsWith("rotation")) {
                    r = V.mm(r, V.rot(Character.toUpperCase(c.charAt(0)), row[j.start + k]));
                }
            }
            if (j.parent == null) {
                gp.put(j.name, tr);
                gr.put(j.name, r);
            } else {
                double[] pr = gr.get(j.parent.name);
                gp.put(j.name, V.add(gp.get(j.parent.name), V.mv(pr, tr)));
                gr.put(j.name, V.mm(pr, r));
            }
        }
        Pose p = new Pose();
        for (Map.Entry<String, String> e : roles.entrySet()) {
            p.pos.put(e.getKey(), gp.get(e.getValue()));
        }
        if (!roles.containsKey("neck")) {
            p.pos.put("neck", gp.get(roles.get("head")));
        }
        p.headRot = gr.get(roles.get("head"));
        return p;
    }
}

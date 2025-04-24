package fabric.humnyas.undershadowed.math;

import net.minecraft.util.math.Vec2f;

import java.util.*;

@SuppressWarnings("unused")
public class PolygonMath {
    // Polygon processing
    public static List<Vec2f> convexHull(List<Vec2f> points) {
        if (points.size() <= 3) return new ArrayList<>(points);
        Stack<Vec2f> stack = new Stack<>();

        // Finds the lowest X and Y (anchor point)
        points.sort((a, b) -> {
            int cmp = Float.compare(a.y, b.y);
            if (cmp == 0) return Float.compare(a.x, b.x);
            return cmp;
        });
        Vec2f p0 = points.get(0);

        sortByAngle(points, p0);

        stack.push(p0);
        for (Vec2f p : points.subList(1, points.size())) {
            while (stack.size() >= 2) {
                Vec2f q = stack.get(stack.size() - 2);
                Vec2f r = stack.peek();
                if (crossProduct(q, r, p) <= 0) stack.pop();
                else break;
            }
            stack.push(p);
        }

        return new ArrayList<>(stack);
    } // Convex hull via Graham scan

    public static List<List<Vec2f>> earClipPolygon(List<Vec2f> polygon) {
        List<List<Vec2f>> triangles = new ArrayList<>();
        List<Vec2f> vertices = new ArrayList<>(polygon);

        int loopLimit = 100;
        while (vertices.size() >= 3 && loopLimit-- > 0) {
            boolean earFound = false;

            for (int i = 0; i < vertices.size(); i++) {
                Vec2f prev = getCircular(vertices, i - 1);
                Vec2f curr = getCircular(vertices, i);
                Vec2f next = getCircular(vertices, i + 1);

                if (crossProduct(prev, curr, next) < 0) continue;

                boolean hasPointInside = false;
                for (int j = 0; j < vertices.size(); j++) {
                    if (j == (i - 1 + vertices.size()) % vertices.size() ||
                            j == i || j == (i + 1) % vertices.size()) continue;

                    if (isPointInTriangle(vertices.get(j), prev, curr, next)) {
                        hasPointInside = true;
                        break;
                    }
                }

                if (!hasPointInside) {
                    List<Vec2f> triangle = Arrays.asList(prev, curr, next);
                    triangles.add(triangle);
                    vertices.remove(i);
                    earFound = true;
                    break;
                }
            }

            if (!earFound) {
                break;
            }
        }

        return triangles;
    } // Turns a polygon into triangles

    public static List<Vec2f> removeColinearPoints(List<Vec2f> vertices, float epsilon) {
        List<Vec2f> cleaned = new ArrayList<>();
        int n = vertices.size();
        for (int i = 0; i < n; i++) {
            Vec2f prev = vertices.get((i - 1 + n) % n);
            Vec2f curr = vertices.get(i);
            Vec2f next = vertices.get((i + 1) % n);

            if (!isColinear(prev, curr, next, epsilon)) {
                cleaned.add(curr);
            }
        }
        return cleaned;
    }

    public static boolean isClockwise(List<Vec2f> poly) {
        float sum = 0f;
        for (int i = 0; i < poly.size(); i++) {
            Vec2f current = poly.get(i);
            Vec2f next = poly.get((i + 1) % poly.size());
            sum += (next.x - current.x) * (next.y + current.y);
        }
        return sum > 0;
    }


    // Geometric Utilities
    public static float crossProduct(Vec2f a, Vec2f b, Vec2f c) {
        float
                abx = b.x - a.x,
                aby = b.y - a.y,
                acx = c.x - a.x,
                acy = c.y - a.y;
        return abx * acy - aby * acx;
    }

    private static boolean isPointInTriangle(Vec2f pt, Vec2f v1, Vec2f v2, Vec2f v3) {
        float d1 = crossProduct(pt, v1, v2);
        float d2 = crossProduct(pt, v2, v3);
        float d3 = crossProduct(pt, v3, v1);

        boolean hasNeg = (d1 < 0) || (d2 < 0) || (d3 < 0);
        boolean hasPos = (d1 > 0) || (d2 > 0) || (d3 > 0);

        return !(hasNeg && hasPos);
    }

    private static boolean isColinear(Vec2f a, Vec2f b, Vec2f c, float epsilon) {
        float area = Math.abs((a.x * (b.y - c.y) + b.x * (c.y - a.y) + c.x * (a.y - b.y)) / 2f);
        return area < epsilon;
    }

    public static boolean areClose(Vec2f a, Vec2f b, float epsilon) {
        return Math.abs(a.x - b.x) < epsilon && Math.abs(a.y - b.y) < epsilon;
    }

    public static void sortByAngle(List<Vec2f> points, Vec2f anchor) {
        points.sort((a, b) -> {
            double angleA = Math.atan2(a.y - anchor.y, a.x - anchor.x);
            double angleB = Math.atan2(b.y - anchor.y, b.x - anchor.x);
            return Double.compare(angleA, angleB);
        });
    } // Sorts counter-clockwise from around the anchor

    // Generic helpers
    public static List<Vec2f> removeNearDuplicates(List<Vec2f> entry, float epsilon) {
        List<Vec2f> uniqueVertices = new ArrayList<>();
        for (Vec2f v : entry) {
            boolean exists = false;
            for (Vec2f u : uniqueVertices) {
                if (PolygonMath.areClose(u, v, epsilon)) {
                    exists = true;
                    break;
                }
            }
            if (!exists) uniqueVertices.add(v);
        }
        return uniqueVertices;
    }

    public static Vec2f computeCenter(List<Vec2f> vertices) {
        float sumX = 0f, sumY = 0f;
        for (Vec2f v : vertices) {
            sumX += v.x;
            sumY += v.y;
        }
        return new Vec2f(sumX / vertices.size(), sumY / vertices.size());
    }

    public static <T> T getCircular(List<T> list, int index) {
        int size = list.size();
        return list.get((index % size + size) % size);
    }
}

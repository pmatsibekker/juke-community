package com.example.annotations;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.Comparator.comparingLong;
import static java.util.stream.Collectors.counting;

/**
 * Plain Java utility — kept alongside the annotation examples because
 * it's referenced from older Juke tutorials. Not Juke-related itself;
 * lives here so existing links in COMMUNITY_GUIDE.md still resolve.
 */
public class MinMax {

    public static void fib(int n) {
        Stream<long[]> fibonacciStream = Stream.iterate(
                new long[]{1, 1},
                p -> new long[]{p[1], p[0] + p[1]}
        );

        fibonacciStream
                .limit(n)
                .map(p -> p[0])
                .forEach(System.out::println);
    }

    public static Integer getSmallest(List<Integer> list) {
        Map<Integer, Long> valueMap = list.stream()
                .collect(Collectors.groupingBy(Integer::intValue, counting()));

        Map.Entry<Integer, Long> maxValue = valueMap.entrySet().stream()
                .max(comparingLong(Map.Entry::getValue))
                .get();

        return maxValue.getKey();
    }

    public static void main(String[] args) throws IOException {
        fib(2);

        List<Integer> list = Arrays.asList(1, 5, 2, 2, 5, 3, 3, 3, 4, 5);
        int small = getSmallest(list);
        System.out.println(small);
    }
}

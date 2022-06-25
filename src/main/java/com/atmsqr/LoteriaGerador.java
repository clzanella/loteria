package com.atmsqr;

import java.io.*;
import java.lang.reflect.Array;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class LoteriaGerador {

    // Quina
    public static void main(String[] args) {

        System.out.println("Carregando histórico de sorteios...");
        List<Concurso> concursos = carregarDoCsv("historico/quina_asloterias_ate_concurso_5880_sorteio.xlsx - quina_www.asloterias.com.br.csv", null);

        System.out.println("Calculando ranking dos números...");

        List<Integer> numeros = IntStream.rangeClosed(1, 80).boxed().collect(Collectors.toList());

        Map<Integer, Integer> rankingNumeros = new HashMap<>();

        for(int num : numeros){
            rankingNumeros.put(num, 0);
        }

        int oddCount = 0;
        int evenCount = 0;

        for(Concurso conc : concursos){
            for(int num : conc.dezenas){
                int count = rankingNumeros.get(num) + 1;
                rankingNumeros.put(num, count);

                if ( (num & 1) == 0 ) {
                    // par
                    evenCount++;
                } else {
                    // impar
                    oddCount++;
                }
            }
        }

        List<Map.Entry<Integer, Integer>> list = rankingNumeros.entrySet().stream().collect(Collectors.toList());
        list = list.stream().sorted((o1, o2) -> o2.getValue() - o1.getValue()).collect(Collectors.toList());

        for(Map.Entry<Integer, Integer> entry : list){
            System.out.println(entry.getKey() + " - sorteios: " + entry.getValue());
        }

        System.out.println();
        System.out.println("Contagem de pares: " + evenCount + " Contagem de impares: " + oddCount);

    }

    // Megasena
    public static void main2(String[] args) {

        System.out.println("Carregando histórico de sorteios...");
        ExecutorService executorService = Executors.newFixedThreadPool(10);
        List<Concurso> concursos = null;
        try {
            concursos = carregarDoCsv("historico/mega_sena_asloterias_ate_concurso_2493_sorteio.xlsx - mega_sena_www.asloterias.com.br.csv", executorService);
        } finally {
            executorService.shutdown();
        }

        Scanner teclado = new Scanner(System.in);
        System.out.println("Quantos jogos você quer fazer?");
        int numJogos = teclado.nextInt();
        System.out.println("Quantas dezenas? (padrão 6 dezenas)");
        int dezenas = teclado.nextInt();

        List<Integer> numeros = IntStream.rangeClosed(1, 60).boxed().collect(Collectors.toList());

        for (int i = 1; i <= numJogos; i++) {
            List<Integer> jogo = new ArrayList<>();
            Collections.shuffle(numeros);

            for (int j = 0; j < dezenas; j++){
                int numero = numeros.get(j);
                jogo.add(numero);
                System.out.println(numero);
            }

            Collections.sort(jogo); // melhor para comparar

            int somaJogo = jogo.stream().mapToInt(Integer::intValue).sum();

            List<Concurso> jaSorteados = concursos.stream().filter(c -> c.somaConcurso == somaJogo && allequal(Arrays.asList(jogo, c.dezenas))).collect(Collectors.toList());
            if(jaSorteados.size() > 0){
                System.out.println("Já sorteado em: ");
                for(Concurso conc : jaSorteados) System.out.println(" " + conc.numeroConcurso + " - " + conc.dataConcurso);
            }

            System.out.println();
        }
    }

    // Todos os resultados da mega sena: https://asloterias.com.br/download-todos-resultados-mega-sena
    public static List<Concurso> carregarDoCsv(String arquivoCsv, ExecutorService executorService){

        List<Concurso> concursos = new ArrayList<>();
        List<CompletableFuture<Void>> tasks = new ArrayList<>();

        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");

        try(
                InputStream stream = LoteriaGerador.class.getClassLoader().getResourceAsStream(arquivoCsv);
                BufferedReader reader = new BufferedReader(new InputStreamReader(stream))
        ){

            String line;
            while ((line = reader.readLine()) != null) {

                if(line.length() == 0){
                    continue;
                }

                String[] segments = line.split(",");

                if(segments[0].equals("Concurso")){
                    // salta o cabeçalho
                    continue;
                }

                Concurso concurso = new Concurso();

                concurso.numeroConcurso = Integer.parseInt(segments[0]);
                concurso.dataConcurso = LocalDate.parse(segments[1], formatter);
                concurso.dezenas = Arrays.asList(
                        Integer.parseInt(segments[2]),
                        Integer.parseInt(segments[3]),
                        Integer.parseInt(segments[4]),
                        Integer.parseInt(segments[5]),
                        Integer.parseInt(segments[6])
                );

                if(concurso.dezenas.size() > 7){
                    concurso.dezenas.add(Integer.parseInt(segments[7]));
                }

                Collections.sort(concurso.dezenas); // para facilitar a comparação

                concursos.add(concurso);

                if(executorService != null){
                    tasks.add(CompletableFuture.runAsync(new ConcursoProcessamento(concurso), executorService));
                }
            }

        }catch (IOException e) {
            throw new IllegalStateException(e);
        }

        CompletableFuture.allOf(tasks.toArray(new CompletableFuture<?>[0])).join();
        return concursos;
    }

    public static <X> boolean allequal( List<Collection<X>> listOfColls ){
        if( listOfColls.size() <= 1 ) return true;
        int nel = listOfColls.get(0).size();
        for( int i = 1; i < listOfColls.size(); ++i ){
            if( listOfColls.get(i).size() != nel ) return false;
        }
        for( int i = 1; i < listOfColls.size(); ++i ){
            if( ! listOfColls.get(i).equals( listOfColls.get(0) ) ) return false;
        }
        return true;
    }

    static class Concurso {
        public LocalDate dataConcurso;
        public int numeroConcurso;
        public List<Integer> dezenas = new ArrayList<>();
        public List<Integer> somasQuadra = new ArrayList<>();
        public List<Integer> somasQuina = new ArrayList<>();
        public int somaConcurso;
    }

    static class ConcursoProcessamento implements Runnable{

        private Concurso concurso;

        public ConcursoProcessamento(Concurso concurso){
            this.concurso = concurso;
        }

        @Override
        public void run() {

            // calcular combinações da quadra
            Combinacao<Integer> combinacaoQuadra = new Combinacao<Integer>(Integer.class, concurso.dezenas.toArray(new Integer[0]), 4);

            //int count = 0;
            while (combinacaoQuadra.hasNext()){
                //count++;
                Integer[] saida = combinacaoQuadra.next();
                Arrays.sort(saida); // para facilitar a comparação
                int soma = Arrays.stream(saida).mapToInt(Integer::intValue).sum();
                //System.out.println("Quadra " + Arrays.toString(saida) + " sum: " + soma);
                concurso.somasQuadra.add(soma);
            }

            // calcular combinações da quina
            Combinacao<Integer> combinacaoQuina = new Combinacao<Integer>(Integer.class, concurso.dezenas.toArray(new Integer[0]), 5);

            //int count = 0;
            while (combinacaoQuina.hasNext()){
                //count++;
                Integer[] saida = combinacaoQuina.next();
                Arrays.sort(saida); // para facilitar a comparação
                int soma = Arrays.stream(saida).mapToInt(Integer::intValue).sum();
                //System.out.println("Quadra " + Arrays.toString(saida) + " sum: " + soma);
                concurso.somasQuina.add(soma);
            }

            // calcular soma da sena
            concurso.somaConcurso = Arrays.stream(concurso.dezenas.toArray(new Integer[0])).mapToInt(Integer::intValue).sum();
        }
    }

    // https://daemoniolabs.wordpress.com/2011/07/04/gerando-combinacoes-usando-java/
    static class Combinacao<T> {
        private Class<T> clazz;
        private int r ;
        private T[] entrada ;
        private int MAX ;
        private int N ;

        /** se r e' zero entao iremos fazer todas
         * as combinacoes (com qualquer quantidade
         * de elementos).
         */
        public Combinacao(Class<T> clazz, T[] entrada, int r) {
            this.clazz = clazz;
            this.r = r ;
            this.entrada = entrada ;
            this.MAX = ~(1 << entrada.length) ;
            this.N = 1;
        }

        /** Retorna true quando ha pelo menos
         * uma combinacao disponivel.
         */
        public boolean hasNext() {
            if ( r != 0 ) {
                while ( ((this.N & this.MAX) != 0) && (countbits() != r) ) N+=1 ;
            }

            return (this.N & this.MAX) != 0;
        }

        /** Retorna a quantidade de bits ativos (= 1)
         * de N.
         */
        private int countbits() {
            int i;
            int c;

            i = 1;
            c = 0;
            while ( (this.MAX & i) != 0 ) {
                if ( (this.N & i) != 0) {
                    c++;
                }
                i = i << 1 ;
            }

            return c ;
        }

        /** Util para obter o tamanho da saida. Esse
         * tamanho e' o numero de posicoes do vetor
         * retornado por next.
         */
        public int getSaidaLength() {
            if (r != 0) {
                return r;
            }

            return this.countbits();
        }

        /** Retorna uma combinacao.
         *
         * ATENCAO: Sempre use next() quando se
         * tem certeza que ha uma combinacao
         * disponivel. Ou seja, sempre use next()
         * quando hasNext() retornar true.
         */
        public T[] next() {
            int saida_index, entrada_index, i;

            T[] saida = (T[]) Array.newInstance(clazz, this.getSaidaLength());

            entrada_index = 0;
            saida_index = 0;
            i = 1;

            while ((this.MAX & i) != 0) {
                if ((this.N & i) != 0) {
                    saida[saida_index] = entrada[entrada_index];
                    saida_index += 1;
                }
                entrada_index += 1;
                i = i << 1;
            }

            N += 1;

            return saida;
        }
    }

}

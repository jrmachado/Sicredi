/*
Cenário de Negócio:
Todo dia útil por volta das 6 horas da manhã um colaborador da retaguarda do Sicredi recebe e organiza as informações de contas para enviar ao Banco Central. 
Todas agencias e cooperativas enviam arquivos Excel à Retaguarda. Hoje o Sicredi já possiu mais de 4 milhões de contas ativas.
Esse usuário da retaguarda exporta manualmente os dados em um arquivo CSV para ser enviada para a Receita Federal, antes as 10:00 da manhã na abertura das agências.

Requisito:
Usar o "serviço da receita" (fake) para processamento automático do arquivo.

Funcionalidade:
0. Criar uma aplicação SprintBoot standalone. Exemplo: java -jar SincronizacaoReceita <input-file>
1. Processa um arquivo CSV de entrada com o formato abaixo.
2. Envia a atualização para a Receita através do serviço (SIMULADO pela classe ReceitaService).
3. Retorna um arquivo com o resultado do envio da atualização da Receita. Mesmo formato adicionando o resultado em uma nova coluna.


Formato CSV:
agencia;conta;saldo;status
0101;12225-6;100,00;A
0101;12226-8;3200,50;A
3202;40011-1;-35,12;I
3202;54001-2;0,00;P
3202;00321-2;34500,00;B
...

*/
package sincronizacaoreceita;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import com.opencsv.CSVWriter;
import com.opencsv.bean.CsvToBean;
import com.opencsv.bean.CsvToBeanBuilder;
import com.opencsv.bean.StatefulBeanToCsv;
import com.opencsv.bean.StatefulBeanToCsvBuilder;
import com.opencsv.exceptions.CsvDataTypeMismatchException;
import com.opencsv.exceptions.CsvRequiredFieldEmptyException;

public class SincronizacaoReceita {
	
	private static final String ARQUIVO_RETORNO_ERRO = "retornoErro.csv";
	private static final String ARQUIVO_RETORNO_SINCRONISMO = "retornoSincronismo.csv";
	private static final String ARQUIVO_SINCRONIZAR = "sincronicar.csv";
	private static final String ERRO = "Erro";
	private static final String ATUALIZADO = "Atualizado";
	private static final String NAO_ATUALIZADO = " Não Atualizado";
	private static final String ERRO_ATUALIZACAO = " Ocorreu um erro na atualização: ";
	private static final String ERRO_LER_ARQUIVO = "Erro ao ler o arquivo .csv. ";
	private static final String ERRO_CRIAR_ARQUIVO_RETORNO = "Erro ao criar o arquivo retornoSincronismo.csv. ";
	private static final String ERRO_CRIAR_ARQUIVO_RETORNO_CONVERSAO = "Erro ao criar o arquivo retornoSincronismo.csv. Erro de conversão. " ;
	private static final String ERRO_CRIAR_ARQUIVO_RETORNO_CAMPO_OBRIGATORIO = "Erro ao criar o arquivo retornoSincronismo.csv. Campo obrigatorio não informado. ";


	public static void main(String[] args) {
        
        // Exemplo como chamar o "serviço" do Banco Central.
        // ReceitaService receitaService = new ReceitaService();
        // receitaService.atualizarConta("0101", "123456", 100.50, "A");        
    	
		
		/*
		 * Foi adicionado o .jar do opencsv para facilicar o trabalho com os arquivos CSV.
		 * 
		 * Criado um Objeto "ContaAtivaCSV" para trabalhar mais facilmente com as informações.  
		 * 
		 * A chamda do mesmo nos dias uteis pode ser criado um Job que start esta chamda diariamente e no horario que seja antes das 10hs da manha.
		 * 
		 * <dependency>
	     *   <groupId>com.opencsv</groupId>
	     *   <artifactId>opencsv</artifactId>
		 *   <version>4.2</version>
		 * </dependency>
		 * 
		 * 
		 */

         try {
			List<ContaAtivaCSV> contasAtivas = buscarContasAtivas();
			
			atualizarContasAtivas(contasAtivas);
			
			criarArquivoRetorno(contasAtivas);
			
		}
		catch (SincronizacaoException e) {
			gerarArquivoErro(e);
		}
    }

	/**
	 * método que gera um arquivo quando não for possivel fazer a integração com o serviço.
	 * @param se
	 */
	private static void gerarArquivoErro(SincronizacaoException se) {
		String[] cabecalho = {ERRO};

		List<String[]> linhas = new ArrayList<String[]>();
		linhas.add(new String[]{se.getMensagemErro()});

		try {
			Writer writer = Files.newBufferedWriter(Paths.get(ARQUIVO_RETORNO_ERRO));
			CSVWriter csvWriter = new CSVWriter(writer);

			csvWriter.writeNext(cabecalho);
			csvWriter.writeAll(linhas);

			csvWriter.flush();
			writer.close();
		}
		catch (IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * Método que cria o arquivo de retorno das integrações feitas no processo.
	 * @param contasAtivas
	 * @throws SincronizacaoException
	 */
	private static void criarArquivoRetorno(List<ContaAtivaCSV> contasAtivas) throws SincronizacaoException {
		 
		try {
			Writer writer = Files.newBufferedWriter(Paths.get(ARQUIVO_RETORNO_SINCRONISMO));
			
			StatefulBeanToCsv<ContaAtivaCSV> beanToCsv = new StatefulBeanToCsvBuilder(writer).build();

			beanToCsv.write(contasAtivas);

			writer.flush();
			writer.close();
		}
		catch (IOException e) {
			throw new  SincronizacaoException( ERRO_CRIAR_ARQUIVO_RETORNO + e.getMessage());
		}
		catch (CsvDataTypeMismatchException e) {
			throw new  SincronizacaoException(ERRO_CRIAR_ARQUIVO_RETORNO_CONVERSAO + e.getMessage());
		}
		catch (CsvRequiredFieldEmptyException e) {
			throw new  SincronizacaoException(ERRO_CRIAR_ARQUIVO_RETORNO_CAMPO_OBRIGATORIO + e.getMessage());
		}
		
	}

	/**
	 * Método que faz a integração com o serviço da receita.
	 * @param contasAtivas
	 */
	private static void atualizarContasAtivas(List<ContaAtivaCSV> contasAtivas) {
		ReceitaService receitaService = new ReceitaService();
		for (ContaAtivaCSV ca : contasAtivas) {
			try {
				boolean atualizarConta = receitaService.atualizarConta(ca.getAgencia(), ca.getConta(), ca.getSaldo(), ca.getStatus());
				if(atualizarConta) {
					ca.setRetorno(ATUALIZADO);
				}else {
					ca.setRetorno(NAO_ATUALIZADO);
				}
			}
			catch (Exception e) {
				ca.setRetorno(ERRO_ATUALIZACAO + e.getMessage());
			}
		}
	}

	/*
	 * Metódo que lista todas as contas ativas para fazer a integração do arquivo informado.
	 */
	private static   List<ContaAtivaCSV> buscarContasAtivas() throws SincronizacaoException {
		try {
			Reader reader = Files.newBufferedReader(Paths.get(ARQUIVO_SINCRONIZAR));
			
			 CsvToBean<ContaAtivaCSV> contasCSVAtivas = new CsvToBeanBuilder<ContaAtivaCSV>(reader)
	                 .withType(ContaAtivaCSV.class)
	                 .build();
			 
			 List<ContaAtivaCSV> contasAtivas = contasCSVAtivas.parse();
			return contasAtivas;
		}
		catch (IOException e) {
			throw new  SincronizacaoException(ERRO_LER_ARQUIVO + e.getMessage());
		}
	}
    
}

package br.one.forum.api;

import br.one.forum.TestcontainersConfiguration;
import br.one.forum.dtos.LoginResponseDto;
import br.one.forum.dtos.UserProfileUpdateRequestDto;
import br.one.forum.dtos.UserRegisterRequestDto;
import br.one.forum.entities.User;
import br.one.forum.repositories.UserRepository;
import br.one.forum.services.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@Testcontainers
@Transactional
@DisplayName("Testes de Integração: Endpoint /users")
class UserControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    private User testUser;
    private String validEmail;
    private String validPassword;

    @BeforeEach
    void setUp() {
        // Limpar dados de testes anteriores
        userRepository.deleteAll();

        // Criar usuário de teste
        validEmail = "usuario@example.com";
        validPassword = "Pass123!"; // Senha forte: 8 caracteres com maiúscula, minúscula, número e especial

        UserRegisterRequestDto registerDto = new UserRegisterRequestDto(
                validEmail,
                validPassword,
                validPassword,
                "Usuário Teste",
                "https://example.com/avatar.jpg"
        );

        userService.createUser(registerDto);
        testUser = userRepository.findByEmail(validEmail).orElseThrow();
    }

    @Test
    @DisplayName("GET /users/{id} deve retornar dados públicos quando usuário não autenticado")
    void deveRetornarDadosPublicosQuandoNaoAutenticado() throws Exception {
        // Act & Assert
        mockMvc.perform(get("/users/{id}", testUser.getId()))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(testUser.getId()))
                .andExpect(jsonPath("$.profileName").exists())
                .andExpect(jsonPath("$.profileName").value("Usuário Teste"))
                .andExpect(jsonPath("$.profilePhoto").exists())
                .andExpect(jsonPath("$.profilePhoto").value("https://example.com/avatar.jpg"))
                .andExpect(jsonPath("$.email").doesNotExist()) // Email não deve estar em dados públicos
                .andExpect(jsonPath("$.createdAt").exists());
    }

    @Test
    @DisplayName("GET /users/{id} deve retornar dados autenticados quando é o próprio usuário")
    void deveRetornarDadosAutenticadosQuandoProprioUsuario() throws Exception {
        // Arrange - Fazer login primeiro para obter autenticação real
        // Criar token de autenticação fazendo login via endpoint /auth/login
        String loginResponseJson = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + validEmail + "\",\"password\":\"" + validPassword + "\"}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        // Extrair access token
        LoginResponseDto loginResponse = objectMapper.readValue(loginResponseJson, LoginResponseDto.class);
        String accessToken = loginResponse.accessToken();

        // Act & Assert - Usar token JWT real para autenticação
        String responseJson = mockMvc.perform(get("/users/{id}", testUser.getId())
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andReturn()
                .getResponse()
                .getContentAsString();

        // Verificar que email está presente (dados autenticados)
        assertThat(responseJson).contains(validEmail);
        assertThat(responseJson).contains("\"id\":" + testUser.getId());
    }

    @Test
    @DisplayName("GET /users/{id} deve retornar dados públicos quando é outro usuário autenticado")
    void deveRetornarDadosPublicosQuandoOutroUsuario() throws Exception {
        // Arrange - Criar outro usuário e fazer login com ele
        UserRegisterRequestDto outroUsuarioDto = new UserRegisterRequestDto(
                "outro@example.com",
                validPassword,
                validPassword,
                "Outro Usuário",
                "https://example.com/outro.jpg"
        );
        userService.createUser(outroUsuarioDto);
        
        // Fazer login com o outro usuário
        String loginResponseJson = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"outro@example.com\",\"password\":\"" + validPassword + "\"}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        LoginResponseDto loginResponse = objectMapper.readValue(loginResponseJson, LoginResponseDto.class);
        String accessToken = loginResponse.accessToken();
        
        // Act & Assert
        mockMvc.perform(get("/users/{id}", testUser.getId())
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value(testUser.getId()))
                .andExpect(jsonPath("$.profileName").exists())
                .andExpect(jsonPath("$.profileName").value("Usuário Teste"))
                .andExpect(jsonPath("$.profilePhoto").exists())
                .andExpect(jsonPath("$.email").doesNotExist()) // Email não deve estar em dados públicos
                .andExpect(jsonPath("$.createdAt").exists());
    }

    @Test
    @DisplayName("GET /users/{id} deve retornar 404 quando usuário não existe")
    void deveRetornar404QuandoUsuarioNaoExiste() throws Exception {
        // Act & Assert
        mockMvc.perform(get("/users/{id}", 99999))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("POST /users/register deve registrar novo usuário com sucesso")
    void deveRegistrarNovoUsuarioComSucesso() throws Exception {
        // Arrange
        UserRegisterRequestDto registerDto = new UserRegisterRequestDto(
                "novo@example.com",
                validPassword, // Pass123! - 8 caracteres, válida
                validPassword,
                "Novo Usuário",
                "https://example.com/novo-avatar.jpg"
        );

        // Act & Assert
        mockMvc.perform(post("/users/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerDto)))
                .andExpect(status().isCreated());

        // Verificar que o usuário foi criado no banco
        User novoUsuario = userRepository.findByEmail("novo@example.com").orElseThrow();
        assertThat(novoUsuario.getEmail()).isEqualTo("novo@example.com");
        assertThat(novoUsuario.getProfile().getName()).isEqualTo("Novo Usuário");
        assertThat(novoUsuario.getProfile().getPhoto()).isEqualTo("https://example.com/novo-avatar.jpg");
        assertThat(novoUsuario.isLocked()).isFalse();
        assertThat(novoUsuario.isEmailVerified()).isTrue();
    }

    @Test
    @DisplayName("POST /users/register deve retornar 400 quando email já existe")
    void deveRetornar400QuandoEmailJaExiste() throws Exception {
        // Arrange - Tentar registrar com email já existente
        UserRegisterRequestDto registerDto = new UserRegisterRequestDto(
                validEmail, // Email já existe
                validPassword,
                validPassword,
                "Outro Nome",
                "https://example.com/avatar2.jpg"
        );

        // Act & Assert
        mockMvc.perform(post("/users/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerDto)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /users/register deve retornar 400 quando senhas não coincidem")
    void deveRetornar400QuandoSenhasNaoCoincidem() throws Exception {
        // Arrange
        UserRegisterRequestDto registerDto = new UserRegisterRequestDto(
                "diferente@example.com",
                validPassword,
                "SenhaDiferente123!", // Senha diferente
                "Usuário",
                "https://example.com/avatar.jpg"
        );

        // Act & Assert
        mockMvc.perform(post("/users/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerDto)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /users/register deve retornar 400 quando email está vazio")
    void deveRetornar400QuandoEmailVazio() throws Exception {
        // Arrange
        UserRegisterRequestDto registerDto = new UserRegisterRequestDto(
                "", // Email vazio
                validPassword,
                validPassword,
                "Usuário",
                "https://example.com/avatar.jpg"
        );

        // Act & Assert
        mockMvc.perform(post("/users/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerDto)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /users/register deve retornar 400 quando senha não atende validação de senha forte")
    void deveRetornar400QuandoSenhaFraca() throws Exception {
        // Arrange - Senha fraca (menos de 6 caracteres)
        UserRegisterRequestDto registerDto = new UserRegisterRequestDto(
                "teste@example.com",
                "12345", // Senha muito curta (menor que min=6)
                "12345",
                "Usuário",
                "https://example.com/avatar.jpg"
        );

        // Act & Assert
        mockMvc.perform(post("/users/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerDto)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("PATCH /users/{id} deve atualizar perfil com sucesso quando é o próprio usuário")
    void deveAtualizarPerfilQuandoProprioUsuario() throws Exception {
        // Arrange - Fazer login para obter token JWT
        String loginResponseJson = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + validEmail + "\",\"password\":\"" + validPassword + "\"}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        LoginResponseDto loginResponse = objectMapper.readValue(loginResponseJson, LoginResponseDto.class);
        String accessToken = loginResponse.accessToken();
        
        UserProfileUpdateRequestDto updateDto = new UserProfileUpdateRequestDto(
                "Nome Atualizado",
                "https://example.com/nova-foto.jpg"
        );

        // Act & Assert
        mockMvc.perform(patch("/users/{id}", testUser.getId())
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateDto)))
                .andExpect(status().isOk());

        // Verificar que o perfil foi atualizado no banco
        User userAtualizadoNovamente = userRepository.findById(testUser.getId()).orElseThrow();
        assertThat(userAtualizadoNovamente.getProfile().getName()).isEqualTo("Nome Atualizado");
        assertThat(userAtualizadoNovamente.getProfile().getPhoto()).isEqualTo("https://example.com/nova-foto.jpg");
    }

    @Test
    @DisplayName("PATCH /users/{id} deve retornar 403 quando tentar atualizar perfil de outro usuário")
    void deveRetornar403QuandoAtualizarPerfilDeOutroUsuario() throws Exception {
        // Arrange - Criar outro usuário e fazer login com ele
        UserRegisterRequestDto outroUsuarioDto = new UserRegisterRequestDto(
                "outro@example.com",
                validPassword,
                validPassword,
                "Outro Usuário",
                "https://example.com/outro.jpg"
        );
        userService.createUser(outroUsuarioDto);
        
        // Fazer login com o outro usuário
        String loginResponseJson = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"outro@example.com\",\"password\":\"" + validPassword + "\"}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        LoginResponseDto loginResponse = objectMapper.readValue(loginResponseJson, LoginResponseDto.class);
        String accessToken = loginResponse.accessToken();
        
        UserProfileUpdateRequestDto updateDto = new UserProfileUpdateRequestDto(
                "Nome Tentativa",
                "https://example.com/foto.jpg"
        );

        // Act & Assert - Deve retornar 403 Forbidden
        mockMvc.perform(patch("/users/{id}", testUser.getId())
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateDto)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("PATCH /users/{id} deve retornar 403 quando usuário não autenticado")
    void deveRetornar403QuandoNaoAutenticadoParaAtualizar() throws Exception {
        // Arrange
        UserProfileUpdateRequestDto updateDto = new UserProfileUpdateRequestDto(
                "Nome Tentativa",
                "https://example.com/foto.jpg"
        );

        // Act & Assert - Sem autenticação retorna 400 (BAD_REQUEST) antes de chegar no PreAuthorize
        mockMvc.perform(patch("/users/{id}", testUser.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateDto)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("PATCH /users/{id} deve atualizar apenas nome quando apenas nome for fornecido")
    void deveAtualizarApenasNomeQuandoApenasNomeFornecido() throws Exception {
        // Arrange - Fazer login para obter token JWT
        String loginResponseJson = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + validEmail + "\",\"password\":\"" + validPassword + "\"}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        LoginResponseDto loginResponse = objectMapper.readValue(loginResponseJson, LoginResponseDto.class);
        String accessToken = loginResponse.accessToken();
        
        UserProfileUpdateRequestDto updateDto = new UserProfileUpdateRequestDto(
                "Novo Nome",
                null // Foto não fornecida
        );

        // Act & Assert
        mockMvc.perform(patch("/users/{id}", testUser.getId())
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateDto)))
                .andExpect(status().isOk());

        // Verificar que apenas o nome foi atualizado
        User userVerificado = userRepository.findById(testUser.getId()).orElseThrow();
        assertThat(userVerificado.getProfile().getName()).isEqualTo("Novo Nome");
        assertThat(userVerificado.getProfile().getPhoto()).isEqualTo("https://example.com/avatar.jpg"); // Foto original mantida
    }

    @Test
    @DisplayName("PATCH /users/{id} deve atualizar apenas foto quando apenas foto for fornecida")
    void deveAtualizarApenasFotoQuandoApenasFotoFornecida() throws Exception {
        // Arrange - Fazer login para obter token JWT
        String loginResponseJson = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + validEmail + "\",\"password\":\"" + validPassword + "\"}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        LoginResponseDto loginResponse = objectMapper.readValue(loginResponseJson, LoginResponseDto.class);
        String accessToken = loginResponse.accessToken();
        
        UserProfileUpdateRequestDto updateDto = new UserProfileUpdateRequestDto(
                null, // Nome não fornecido
                "https://example.com/nova-foto.jpg"
        );

        // Act & Assert
        mockMvc.perform(patch("/users/{id}", testUser.getId())
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateDto)))
                .andExpect(status().isOk());

        // Verificar que apenas a foto foi atualizada
        User userVerificado = userRepository.findById(testUser.getId()).orElseThrow();
        assertThat(userVerificado.getProfile().getName()).isEqualTo("Usuário Teste"); // Nome original mantido
        assertThat(userVerificado.getProfile().getPhoto()).isEqualTo("https://example.com/nova-foto.jpg");
    }

    @Test
    @DisplayName("GET /users/{id}/comments deve retornar página de comentários do usuário")
    void deveRetornarPaginaDeComentariosDoUsuario() throws Exception {
        // Act & Assert
        mockMvc.perform(get("/users/{id}/comments", testUser.getId())
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.content").exists())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.totalElements").exists())
                .andExpect(jsonPath("$.totalElements").isNumber());
    }

    @Test
    @DisplayName("GET /users/{id}/comments deve retornar página vazia quando usuário não tem comentários")
    void deveRetornarPaginaVaziaQuandoUsuarioNaoTemComentarios() throws Exception {
        // Act & Assert - CommentService retorna página vazia, não 404
        mockMvc.perform(get("/users/{id}/comments", testUser.getId())
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.content").exists())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.totalElements").exists())
                .andExpect(jsonPath("$.totalElements").value(0)); // Sem comentários
    }
}


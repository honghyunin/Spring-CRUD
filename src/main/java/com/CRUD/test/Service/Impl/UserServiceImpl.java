package com.CRUD.test.Service.Impl;

import com.CRUD.test.Service.EmailSenderService;
import com.CRUD.test.Service.UserService;
import com.CRUD.test.advice.exception.UserAlreadyExistsException;
import com.CRUD.test.advice.exception.UserNotFoundException;
import com.CRUD.test.advice.exception.UserNotmatch;
import com.CRUD.test.domain.User;
import com.CRUD.test.dto.UserLoginDto;
import com.CRUD.test.dto.UserResponseDto;
import com.CRUD.test.dto.UserSaveRequestDto;
import com.CRUD.test.dto.UserUpdateRequestDto;
import com.CRUD.test.repository.UserRepository;
import com.CRUD.test.security.JwtTokenProdvider;
import com.CRUD.test.util.RedisUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;


@RequiredArgsConstructor
@Service
public class UserServiceImpl implements UserService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProdvider jwtTokenProdvider;
    private final RedisUtil redisUtil;
    private final EmailSenderService emailSenderService;
    @Transactional
    @Override
    public Long signup(UserSaveRequestDto user) {
        if(userRepository.findById(user.getId()) != null){ // 요청으로 받아온 user의 id가 DB상에서 존재하는 지 검사
            throw new UserAlreadyExistsException();
        }
        emailSenderService.sendEmail(user); // 회원가입을 했을 경우 이메일 인증을 통해 인증된 유저로써 서비스를 이용할 수 있음

        user.setPw(passwordEncoder.encode(user.getPw())); // password 암호화

        return userRepository.save(user.toEntity()).getIdx(); // 우선적으로 DB에 저장함
    }

    public Map<String, String> login(UserLoginDto user){

        User findUser = userRepository.findById(user.getId());

        if(findUser == null){throw new UserNotFoundException("유저를 찾을 수 없습니다");}

        if(!passwordEncoder.matches(user.getPw(), findUser.getPw()))
        {
            throw new UserNotmatch("일치하지 않는 비밀번호입니다");}

        String AccessToken = jwtTokenProdvider.createToken(user.getId(), user.toEntity().getRoles());
        String RefreshToken = jwtTokenProdvider.createRefreshToken();

        redisUtil.deleteData(user.getId()); // redis에 값을 삽입하기 전 해당 아이디의 refreshToken 삭제
        redisUtil.setDataExpire(findUser.getId(), RefreshToken, JwtTokenProdvider.REFRESH_TOKEN_VAILD_TIME);

        Map<String, String> map = new HashMap<>();
        map.put("email", user.getId());
        map.put("AccessToken", "Bearer"+AccessToken);
        map.put("RefreshToken", "Bearer" +RefreshToken);

        return map;
    }
    @Override
    public UserResponseDto findById(Long idx) {
            User user = userRepository.findById(idx)
                    .orElseThrow(UserNotFoundException::new);
        return new UserResponseDto(user);
    }

    @Transactional
    @Override
    public Long update(UserUpdateRequestDto requestDto) {
        Long idx = requestDto.getIdx(); // idx 지정
        User user = userRepository.findById(idx).orElseThrow(UserNotFoundException :: new);  // localDB에 존재하는 idx의 값을 찾고 그 값이 없을 경우의 예외를 처리함
        user.update(requestDto.getId(), requestDto.getPw());
        return idx;
    }

    public void logout(){
        redisUtil.deleteData(currentUserUtil.getCurrentUser().getId());
    }

    public String delete(Long idx) {
        userRepository.deleteById(idx); // 내 localDB에 존재하는 idx의 열을 삭제함
        return  idx +"is Delete" ;
    }


}

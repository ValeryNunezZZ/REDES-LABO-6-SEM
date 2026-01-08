LIBRARY IEEE;
USE IEEE.STD_LOGIC_1164.ALL;

ENTITY condec IS
    PORT(
        CLK, CLR: IN STD_LOGIC;
        X: IN STD_LOGIC_VECTOR(1 DOWNTO 0);
        E: IN STD_LOGIC_VECTOR(3 DOWNTO 0);
        DISPLAY: OUT STD_LOGIC_VECTOR(6 DOWNTO 0)
    );
END ENTITY;

ARCHITECTURE practicaSiete OF condec IS
    --Para guardar de 0 a 27000
    signal cnt : unsigned(24 DOWNTO 0) := (others => '0');

    BEGIN
    
    --Pulso de reloj 
    reloj : process(clk, CLR)
    begin
        
    if rising_edge(clk) then
        
        --Incrementa a contador hasta que llegue a 27000
        cnt <= cnt + 1;

        if(cnt = 27000000) then
            
            --SI X ES 1 => RETIENE

            --SI X ES 0 => CONTADOR
            IF X = '0' THEN

                --FLIP FLOP JK
                Q(7) <= (NOT X) AND Q(1) AND Q(0) AND Q(2) AND Q(3) AND Q(4) AND Q(5) AND Q(6);
                NQ(7) <= (NOT ((NOT X) AND Q(1) AND Q(0) AND Q(2) AND Q(3) AND Q(4) AND Q(5) AND Q(6)));

                Q(6) <= (NOT X) AND Q(1) AND Q(0) AND Q(2) AND Q(3) AND Q(4) AND Q(5);
                NQ(6) <= (NOT ((NOT X) AND Q(1) AND Q(0) AND Q(2) AND Q(3) AND Q(4) AND Q(5)));

                Q(5) <= (NOT X) AND Q(1) AND Q(0) AND Q(2) AND Q(3) AND Q(4);
                NQ(5) <= (NOT ((NOT X) AND Q(1) AND Q(0) AND Q(2) AND Q(3) AND Q(4)));

                Q(4) <= (NOT X) AND Q(1) AND Q(0) AND Q(2) AND Q(3);
                NQ(4) <= (NOT ((NOT X) AND Q(1) AND Q(0) AND Q(2) AND Q(3)));

                Q(3) <= (NOT X) AND Q(1) AND Q(0) AND Q(2);
                NQ(3) <= (NOT ((NOT X) AND Q(1) AND Q(0) AND Q(2)));

                Q(2) <= (NOT X) AND Q(1) AND Q(0);
                NQ(2) <= (NOT ((NOT X) AND Q(1) AND Q(0)));

                Q(1) <= (NOT X) AND Q(0);
                NQ(1) <= (NOT ((NOT X) AND Q(0)));

                Q(0) <= (NOT X);
                NQ(0) <= (NOT (NOT X));
         
            ELSE
                Q <= Q;
            END IF;

            Y <= (NOT X) AND Q(0) AND Q(1) AND Q(2) AND Q(3) AND Q(4) AND Q(5) AND Q(6) AND Q(7);
 
            cnt <= (others => '0');

        end if;
    end if;

    IF CLR = '1' THEN
        Q <= "00000000";
        NQ <= "11111111";
    END IF;


    end process;
END ARCHITECTURE practicaSiete;
